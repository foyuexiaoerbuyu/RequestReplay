package com.requestrecorder.core.queue;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.database.RecorderRepository;
import com.requestrecorder.core.model.RecordData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步写入队列。
 * <p>
 * 约束：
 * <ul>
 *   <li>业务线程只做入队，队列满时立即丢弃并计数，绝不让采集阻塞业务响应；</li>
 *   <li>写入失败按 {@code max-retries} 重试，仍失败只记录日志与计数；</li>
 *   <li>关闭时尽力排空队列，超过 {@code shutdown-timeout-ms} 则强制丢弃剩余任务。</li>
 * </ul>
 */
public final class AsyncRecordQueue implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncRecordQueue.class);

    private final RecorderRepository repository;
    private final RecorderSettings settings;
    private final BlockingQueue<Runnable> queue;
    private final ThreadPoolExecutor executor;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    /** 正在组装中（尚未入队）的采集请求数，用于优雅停机与排空判定。 */
    private final AtomicInteger capturing = new AtomicInteger();

    private volatile boolean closed;

    public AsyncRecordQueue(RecorderRepository repository, RecorderSettings settings) {
        this.repository = repository;
        this.settings = settings;
        int capacity = Math.max(1, settings.getQueueCapacity());
        this.queue = new ArrayBlockingQueue<Runnable>(capacity);
        int workers = Math.max(1, settings.getWorkerThreads());
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS, queue,
                runnable -> {
                    Thread thread = new Thread(runnable, "request-recorder-writer");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> {
                    // 队列满：丢弃，绝不回调业务线程执行
                    dropped.incrementAndGet();
                    if (!settings.isDropWhenFull()) {
                        log.warn("RequestRecorder 队列已满，采集中断（dropped={}）", dropped.get());
                    }
                });
        this.executor.prestartAllCoreThreads();
    }

    /**
     * 提交一条采集记录。任何异常都不会向外抛出。
     *
     * @return true 表示已受理（异步入队或同步写入成功）
     */
    public boolean submit(final RecordData record) {
        if (record == null || closed) {
            return false;
        }
        submitted.incrementAndGet();
        if (!settings.isAsyncEnabled()) {
            return writeWithRetry(record);
        }
        try {
            executor.execute(() -> writeWithRetry(record));
            return true;
        } catch (RejectedExecutionException e) {
            dropped.incrementAndGet();
            return false;
        } catch (Throwable t) {
            dropped.incrementAndGet();
            log.warn("RequestRecorder 入队失败: {}", t.toString());
            return false;
        }
    }

    private boolean writeWithRetry(RecordData record) {
        int attempts = Math.max(0, settings.getMaxRetries()) + 1;
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                repository.insert(record);
                written.incrementAndGet();
                return true;
            } catch (Exception e) {
                last = e;
                if (i + 1 < attempts && settings.getRetryBackoffMs() > 0) {
                    try {
                        Thread.sleep(settings.getRetryBackoffMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        failed.incrementAndGet();
        log.warn("RequestRecorder 写入失败（已重试 {} 次）: {}", attempts - 1,
                last == null ? "unknown" : last.toString());
        return false;
    }

    public QueueStats stats() {
        return new QueueStats(submitted.get(), written.get(), failed.get(), dropped.get(),
                queue.size(), settings.getQueueCapacity());
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public long getWrittenCount() {
        return written.get();
    }

    public long getFailedCount() {
        return failed.get();
    }

    /** 标记一条采集请求开始组装（在途）。 */
    public void captureBegin() {
        capturing.incrementAndGet();
    }

    /** 标记一条采集请求组装结束。 */
    public void captureEnd() {
        capturing.decrementAndGet();
    }

    /** 当前是否处于完全空闲状态：无在途组装、队列空、无写入线程忙碌。 */
    public boolean isIdle() {
        return capturing.get() == 0 && queue.isEmpty() && executor.getActiveCount() == 0;
    }

    /**
     * 阻塞等待队列排空（测试与优雅停机使用）。
     * <p>
     * 判定条件为「在途采集为 0 + 队列空 + 写入线程空闲」，并要求连续两次采样均满足，
     * 避免业务响应已返回、采集尚未入队的瞬间被误判为空闲。
     */
    public boolean awaitDrain(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (isIdle()) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (isIdle()) {
                    return true;
                }
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isIdle();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        long timeout = Math.max(0, settings.getShutdownTimeoutMs());
        // 先给在途组装中的采集一点时间入队，避免停机瞬间丢记录
        long waitUntil = System.currentTimeMillis() + Math.min(500L, timeout);
        while (capturing.get() > 0 && System.currentTimeMillis() < waitUntil) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                int remaining = executor.shutdownNow().size();
                if (remaining > 0) {
                    dropped.addAndGet(remaining);
                    log.warn("RequestRecorder 停机超时，丢弃 {} 条待写记录", remaining);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("RequestRecorder 队列已关闭，{}", stats());
    }
}
