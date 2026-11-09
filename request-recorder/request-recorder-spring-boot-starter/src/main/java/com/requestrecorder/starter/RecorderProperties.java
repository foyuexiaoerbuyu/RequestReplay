package com.requestrecorder.starter;

import com.requestrecorder.core.config.RecorderSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 采集组件配置属性，前缀 {@code request-recorder}。
 * <p>
 * 所有字段（含内嵌配置类）均提供标准 getter/setter，确保 Spring Boot 宽松绑定生效。
 */
@ConfigurationProperties(prefix = "request-recorder")
public class RecorderProperties {

    /** 是否启用采集，默认 true。 */
    private boolean enabled = true;
    /** 项目编码，必填，用于多项目数据隔离。 */
    private String projectCode;
    /** 项目名称，缺省回落为 project-code。 */
    private String projectName;
    /** 项目描述。 */
    private String description;
    /** 单请求/响应正文采集上限（字节），超出即截断。 */
    private int maxBodySize = 1048576;
    /** 不采集的路径（Ant 风格）。 */
    private List<String> excludePaths = new ArrayList<String>(
            Arrays.asList("/actuator/**", "/favicon.ico", "/error", "/request-recorder/**"));
    /** 是否采集 js/css/图片等静态资源。 */
    private boolean recordStaticResources = false;
    /** 是否跳过 WebSocket 握手请求。 */
    private boolean skipWebSocket = true;
    /** 是否跳过 SSE（text/event-stream）请求。 */
    private boolean skipSse = true;
    /** 文件下载响应是否只记录元数据。 */
    private boolean skipFileDownload = true;
    /** 是否解析 multipart 请求头字段（默认只记录元数据）。 */
    private boolean recordMultipart = false;
    /** 启动时表不存在是否自动建表。 */
    private boolean autoInitSchema = false;
    /** 配置非法时是否阻断业务启动（默认 false：仅打印错误并禁用采集）。 */
    private boolean failFast = false;

    private Database database = new Database();
    private Async async = new Async();
    private Masking masking = new Masking();
    private Replay replay = new Replay();

    /** 转换为 core 使用的纯 Java 配置对象。 */
    public RecorderSettings toSettings() {
        RecorderSettings settings = new RecorderSettings();
        settings.setEnabled(enabled);
        settings.setProjectCode(trimToNull(projectCode));
        settings.setProjectName(trimToNull(projectName));
        settings.setDescription(trimToNull(description));
        settings.setJdbcUrl(trimToNull(database.getJdbcUrl()));
        settings.setUsername(database.getUsername());
        settings.setPassword(database.getPassword());
        if (database.getDriverClassName() != null) {
            settings.setDriverClassName(database.getDriverClassName());
        }
        settings.setPoolSize(database.getPoolSize());
        settings.setConnectionTimeoutMs(database.getConnectionTimeoutMs());
        settings.setValidationTimeoutMs(database.getValidationTimeoutMs());
        settings.setAutoInitSchema(autoInitSchema);
        settings.setAsyncEnabled(async.isEnabled());
        settings.setQueueCapacity(async.getQueueCapacity());
        settings.setWorkerThreads(async.getWorkers());
        settings.setDropWhenFull(async.isDropWhenFull());
        settings.setMaxRetries(async.getMaxRetries());
        settings.setRetryBackoffMs(async.getRetryBackoffMs());
        settings.setShutdownTimeoutMs(async.getShutdownTimeoutMs());
        settings.setMaxBodySize(maxBodySize);
        settings.setExcludePaths(excludePaths == null ? new ArrayList<String>() : new ArrayList<String>(excludePaths));
        settings.setRecordStaticResources(recordStaticResources);
        settings.setSkipWebSocket(skipWebSocket);
        settings.setSkipSse(skipSse);
        settings.setSkipFileDownload(skipFileDownload);
        settings.setRecordMultipart(recordMultipart);
        settings.setMaskingEnabled(masking.isEnabled());
        settings.setMaskHeaders(masking.getHeaders() == null ? new ArrayList<String>()
                : new ArrayList<String>(masking.getHeaders()));
        settings.setMaskKeys(masking.getKeys() == null ? new ArrayList<String>()
                : new ArrayList<String>(masking.getKeys()));
        settings.setSkipReplayRequests(replay.isSkipEnabled());
        settings.setReplayHeaderName(replay.getHeaderName());
        settings.setReplayHeaderValue(replay.getHeaderValue());
        settings.setFailFast(failFast);
        return settings;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public boolean isRecordStaticResources() {
        return recordStaticResources;
    }

    public void setRecordStaticResources(boolean recordStaticResources) {
        this.recordStaticResources = recordStaticResources;
    }

    public boolean isSkipWebSocket() {
        return skipWebSocket;
    }

    public void setSkipWebSocket(boolean skipWebSocket) {
        this.skipWebSocket = skipWebSocket;
    }

    public boolean isSkipSse() {
        return skipSse;
    }

    public void setSkipSse(boolean skipSse) {
        this.skipSse = skipSse;
    }

    public boolean isSkipFileDownload() {
        return skipFileDownload;
    }

    public void setSkipFileDownload(boolean skipFileDownload) {
        this.skipFileDownload = skipFileDownload;
    }

    public boolean isRecordMultipart() {
        return recordMultipart;
    }

    public void setRecordMultipart(boolean recordMultipart) {
        this.recordMultipart = recordMultipart;
    }

    public boolean isAutoInitSchema() {
        return autoInitSchema;
    }

    public void setAutoInitSchema(boolean autoInitSchema) {
        this.autoInitSchema = autoInitSchema;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    public Masking getMasking() {
        return masking;
    }

    public void setMasking(Masking masking) {
        this.masking = masking;
    }

    public Replay getReplay() {
        return replay;
    }

    public void setReplay(Replay replay) {
        this.replay = replay;
    }

    /** 独立数据库配置。 */
    public static class Database {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int poolSize = 3;
        private long connectionTimeoutMs = 3000L;
        private long validationTimeoutMs = 2000L;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getValidationTimeoutMs() {
            return validationTimeoutMs;
        }

        public void setValidationTimeoutMs(long validationTimeoutMs) {
            this.validationTimeoutMs = validationTimeoutMs;
        }
    }

    /** 异步写入配置。 */
    public static class Async {
        private boolean enabled = true;
        private int queueCapacity = 5000;
        private int workers = 2;
        private boolean dropWhenFull = true;
        private int maxRetries = 1;
        private long retryBackoffMs = 200L;
        private long shutdownTimeoutMs = 3000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getWorkers() {
            return workers;
        }

        public void setWorkers(int workers) {
            this.workers = workers;
        }

        public boolean isDropWhenFull() {
            return dropWhenFull;
        }

        public void setDropWhenFull(boolean dropWhenFull) {
            this.dropWhenFull = dropWhenFull;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public long getShutdownTimeoutMs() {
            return shutdownTimeoutMs;
        }

        public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
            this.shutdownTimeoutMs = shutdownTimeoutMs;
        }
    }

    /** 脱敏配置。 */
    public static class Masking {
        private boolean enabled = true;
        private List<String> headers = new ArrayList<String>(
                Arrays.asList("authorization", "cookie", "set-cookie", "x-api-key", "proxy-authorization"));
        private List<String> keys = new ArrayList<String>(
                Arrays.asList("password", "passwd", "pwd", "token", "secret", "access_token", "refresh_token",
                        "id_card", "idcard", "mobile", "phone", "bank_card", "cvv"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public List<String> getKeys() {
            return keys;
        }

        public void setKeys(List<String> keys) {
            this.keys = keys;
        }
    }

    /** 防循环配置。 */
    public static class Replay {
        private boolean skipEnabled = true;
        private String headerName = "X-Request-Replay";
        private String headerValue = "true";

        public boolean isSkipEnabled() {
            return skipEnabled;
        }

        public void setSkipEnabled(boolean skipEnabled) {
            this.skipEnabled = skipEnabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getHeaderValue() {
            return headerValue;
        }

        public void setHeaderValue(String headerValue) {
            this.headerValue = headerValue;
        }
    }
}
