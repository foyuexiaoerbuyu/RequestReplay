package com.requestrecorder.core;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.database.RecorderRepository;
import com.requestrecorder.core.model.RecordData;
import com.requestrecorder.core.queue.AsyncRecordQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步写入队列行为测试：同步/异步、重试计数、队列满丢弃、优雅关闭。
 */
class AsyncRecordQueueTest {

    private RecorderSettings settings(int workers, int capacity, int maxRetries, boolean async) {
        RecorderSettings settings = new RecorderSettings();
        settings.setProjectCode("queue-project");
        settings.setWorkerThreads(workers);
        settings.setQueueCapacity(capacity);
        settings.setMaxRetries(maxRetries);
        settings.setRetryBackoffMs(10L);
        settings.setShutdownTimeoutMs(2000L);
        settings.setAsyncEnabled(async);
        return settings;
    }

    private RecordData record() {
        RecordData data = new RecordData();
        data.setProjectCode("queue-project");
        data.setMethod("GET");
        data.setUrl("http://localhost/api/x");
        data.setUri("/api/x");
        data.setRequestTime(System.currentTimeMillis());
        return data;
    }

    /** 可控仓储：记录调用次数，按需抛异常或阻塞。 */
    private RecorderRepository repository(final AtomicInteger calls, final boolean fail,
                                          final long sleepMs) {
        return new RecorderRepository(null, null) {
            @Override
            public void insert(RecordData data) throws SQLException {
                calls.incrementAndGet();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (fail) {
                    throw new SQLException("mock failure");
                }
            }
        };
    }

    @Test
    @DisplayName("同步模式直接写入并计数")
    void syncModeWritesImmediately() {
        AtomicInteger calls = new AtomicInteger();
        RecorderSettings settings = settings(1, 10, 0, false);
        AsyncRecordQueue queue = new AsyncRecordQueue(repository(calls, false, 0), settings);
        try {
            assertTrue(queue.submit(record()));
            assertTrue(queue.getWrittenCount() == 1L);
            assertTrue(calls.get() == 1);
        } finally {
            queue.close();
        }
    }

    @Test
    @DisplayName("写入失败按 max-retries 重试并计入 failed，不抛异常")
    void failedWriteIsRetriedAndCounted() {
        AtomicInteger calls = new AtomicInteger();
        RecorderSettings settings = settings(1, 10, 2, false);
        AsyncRecordQueue queue = new AsyncRecordQueue(repository(calls, true, 0), settings);
        try {
            assertFalse(queue.submit(record()));
            assertTrue(calls.get() == 3, "max-retries=2 应共尝试 3 次，实际: " + calls.get());
            assertTrue(queue.getFailedCount() == 1L);
        } finally {
            queue.close();
        }
    }

    @Test
    @DisplayName("队列满时立即丢弃，不阻塞业务线程")
    void fullQueueDropsWithoutBlocking() {
        AtomicInteger calls = new AtomicInteger();
        RecorderSettings settings = settings(1, 1, 0, true);
        AsyncRecordQueue queue = new AsyncRecordQueue(repository(calls, false, 120), settings);
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                queue.submit(record());
            }
            long cost = System.currentTimeMillis() - start;
            assertTrue(cost < 1000L, "入队不应阻塞业务线程，耗时: " + cost);
            assertTrue(queue.getDroppedCount() > 0L, "队列满应产生丢弃计数");
        } finally {
            queue.close();
        }
    }

    @Test
    @DisplayName("关闭前排空队列，关闭后拒绝新记录")
    void drainOnClose() {
        AtomicInteger calls = new AtomicInteger();
        RecorderSettings settings = settings(2, 100, 0, true);
        AsyncRecordQueue queue = new AsyncRecordQueue(repository(calls, false, 5), settings);
        for (int i = 0; i < 20; i++) {
            queue.submit(record());
        }
        assertTrue(queue.awaitDrain(3000L), "队列应在超时前排空");
        queue.close();
        assertTrue(queue.getWrittenCount() == 20L, "写入条数应为 20，实际: " + queue.getWrittenCount());
        assertFalse(queue.submit(record()));
    }
}
