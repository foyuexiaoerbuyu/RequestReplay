package com.requestrecorder.core.service;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.database.RecorderDataSource;
import com.requestrecorder.core.database.RecorderRepository;
import com.requestrecorder.core.model.ProjectInfo;
import com.requestrecorder.core.queue.AsyncRecordQueue;
import com.requestrecorder.core.queue.QueueStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 采集组件门面：项目注册、表结构检查、统计查询与资源释放。
 */
public final class RecorderService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RecorderService.class);

    private final RecorderSettings settings;
    private final RecorderDataSource dataSource;
    private final RecorderRepository repository;
    private final AsyncRecordQueue queue;
    private final RequestCollector collector;

    private volatile boolean started;
    private volatile boolean projectRegistered;

    public RecorderService(RecorderSettings settings, RecorderDataSource dataSource,
                           RecorderRepository repository, AsyncRecordQueue queue) {
        this.settings = settings;
        this.dataSource = dataSource;
        this.repository = repository;
        this.queue = queue;
        this.collector = new RequestCollector(settings, queue);
    }

    /**
     * 启动采集组件：检查表结构 → 注册项目信息。
     * <p>
     * 任一步失败只打印错误日志，不抛异常，确保业务应用正常启动。
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        checkSchema();
        registerProject();
        log.info("RequestRecorder 已启用: projectCode={}, projectName={}, async={}, workers={}, queueCapacity={}",
                settings.getProjectCode(), settings.resolveProjectName(), settings.isAsyncEnabled(),
                settings.getWorkerThreads(), settings.getQueueCapacity());
    }

    private void checkSchema() {
        try {
            if (settings.isAutoInitSchema()) {
                repository.initSchema();
                log.info("RequestRecorder 已按 auto-init-schema=true 初始化采集表");
                return;
            }
            boolean recordTable = repository.tableExists("http_request_record");
            boolean projectTable = repository.tableExists("http_request_project");
            if (!recordTable || !projectTable) {
                log.error("RequestRecorder 采集表不存在（http_request_record={}, http_request_project={}），"
                                + "请先执行 sql/schema.sql 建表，或配置 request-recorder.auto-init-schema=true；"
                                + "当前采集将无法写入数据库", recordTable, projectTable);
            }
        } catch (Exception e) {
            log.error("RequestRecorder 表结构检查失败（数据库不可用？采集将按可用性自动重试）: {}", e.toString());
        }
    }

    private void registerProject() {
        try {
            repository.upsertProject(new ProjectInfo(settings.getProjectCode(),
                    settings.resolveProjectName(), settings.getDescription()));
            projectRegistered = true;
            log.info("RequestRecorder 项目注册成功: {}", settings.getProjectCode());
        } catch (Exception e) {
            log.error("RequestRecorder 项目注册失败（不影响业务启动，后续请求采集仍会尝试写入）: {}", e.toString());
        }
    }

    public QueueStats stats() {
        return queue.stats();
    }

    public boolean isProjectRegistered() {
        return projectRegistered;
    }

    public boolean isDataSourceHealthy() {
        return dataSource.isHealthy();
    }

    public RecorderSettings getSettings() {
        return settings;
    }

    public RequestCollector getCollector() {
        return collector;
    }

    public RecorderRepository getRepository() {
        return repository;
    }

    public AsyncRecordQueue getQueue() {
        return queue;
    }

    public boolean awaitDrain(long timeoutMs) {
        return queue.awaitDrain(timeoutMs);
    }

    @Override
    public void close() {
        try {
            queue.close();
        } catch (Exception e) {
            log.warn("关闭采集队列异常: {}", e.toString());
        }
        try {
            dataSource.close();
        } catch (Exception e) {
            log.warn("关闭采集数据源异常: {}", e.toString());
        }
        log.info("RequestRecorder 已停止，最终统计: {}", queue.stats());
    }
}
