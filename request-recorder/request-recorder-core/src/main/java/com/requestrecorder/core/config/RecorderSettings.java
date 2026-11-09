package com.requestrecorder.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 采集组件运行参数（纯 Java POJO，不依赖 Spring）。
 * <p>
 * 所有字段均提供标准 getter/setter，避免 Spring Boot 宽松绑定失效。
 * 由 spring-boot-starter 的 {@code RecorderProperties} 转换而来。
 */
public class RecorderSettings {

    private boolean enabled = true;

    /** 项目编码，多项目接入时用于隔离数据，必填。 */
    private String projectCode;
    /** 项目名称，缺省时回落为 projectCode。 */
    private String projectName;
    /** 项目描述，写入 http_request_project.description。 */
    private String description;

    // ---------------- 独立数据库 ----------------
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int poolSize = 3;
    private long connectionTimeoutMs = 3000L;
    private long validationTimeoutMs = 2000L;
    /** 启动时若表不存在，是否自动建表；默认 false，仅打印错误提示。 */
    private boolean autoInitSchema = false;

    // ---------------- 异步写入 ----------------
    private boolean asyncEnabled = true;
    private int queueCapacity = 5000;
    private int workerThreads = 2;
    private boolean dropWhenFull = true;
    private int maxRetries = 1;
    private long retryBackoffMs = 200L;
    private long shutdownTimeoutMs = 3000L;

    // ---------------- 采集范围 ----------------
    /** 单个请求/响应体最大采集字节数，超出部分截断。 */
    private int maxBodySize = 1048576;
    private List<String> excludePaths = new ArrayList<String>(
            Arrays.asList("/actuator/**", "/favicon.ico", "/error", "/request-recorder/**"));
    /** 是否记录静态资源（默认不额外过滤，交由 excludePaths 控制）。 */
    private boolean recordStaticResources = false;
    /** WebSocket 握手与帧不适合缓存，默认跳过。 */
    private boolean skipWebSocket = true;
    /** SSE（text/event-stream）长连接不缓存，默认跳过。 */
    private boolean skipSse = true;
    /** 文件下载响应只记录元数据，不缓存正文。 */
    private boolean skipFileDownload = true;
    /** multipart 上传默认只记录元数据，不解析正文。 */
    private boolean recordMultipart = false;

    // ---------------- 脱敏 ----------------
    private boolean maskingEnabled = true;
    private List<String> maskHeaders = new ArrayList<String>(
            Arrays.asList("authorization", "cookie", "set-cookie", "x-api-key", "proxy-authorization"));
    private List<String> maskKeys = new ArrayList<String>(
            Arrays.asList("password", "passwd", "pwd", "token", "secret", "access_token",
                    "refresh_token", "id_card", "idcard", "mobile", "phone", "bank_card", "cvv"));

    // ---------------- 防循环 ----------------
    /** 回放请求默认不采集，避免回放数据写回采集表形成循环。 */
    private boolean skipReplayRequests = true;
    private String replayHeaderName = "X-Request-Replay";
    private String replayHeaderValue = "true";

    /** 配置非法时是否阻断业务启动。 */
    private boolean failFast = false;

    /**
     * 校验必填项，返回错误描述列表（空列表表示配置合法）。
     *
     * @param checkEnabled 是否把 enabled=false 视为"无需校验"
     */
    public List<String> validate(boolean checkEnabled) {
        List<String> errors = new ArrayList<String>();
        if (checkEnabled && !enabled) {
            return errors;
        }
        if (isBlank(projectCode)) {
            errors.add("request-recorder.project-code 未配置（多项目接入必须指定项目编码）");
        } else if (projectCode.length() > 100) {
            errors.add("request-recorder.project-code 长度超过 100：" + projectCode);
        }
        if (isBlank(jdbcUrl)) {
            errors.add("request-recorder.database.jdbc-url 未配置（采集组件使用独立数据库，必须显式指定）");
        }
        if (poolSize < 1) {
            errors.add("request-recorder.database.pool-size 必须 >= 1，当前：" + poolSize);
        }
        if (queueCapacity < 1) {
            errors.add("request-recorder.async.queue-capacity 必须 >= 1，当前：" + queueCapacity);
        }
        if (workerThreads < 1) {
            errors.add("request-recorder.async.workers 必须 >= 1，当前：" + workerThreads);
        }
        if (maxBodySize < 0) {
            errors.add("request-recorder.max-body-size 不能为负数，当前：" + maxBodySize);
        }
        return errors;
    }

    /** 当前是否采集生效（配置合法且未关闭）。 */
    public boolean isActive() {
        return enabled && !isBlank(projectCode) && !isBlank(jdbcUrl);
    }

    public String resolveProjectName() {
        return isBlank(projectName) ? projectCode : projectName;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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

    public boolean isAutoInitSchema() {
        return autoInitSchema;
    }

    public void setAutoInitSchema(boolean autoInitSchema) {
        this.autoInitSchema = autoInitSchema;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
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
        this.excludePaths = excludePaths == null ? new ArrayList<String>() : excludePaths;
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

    public boolean isMaskingEnabled() {
        return maskingEnabled;
    }

    public void setMaskingEnabled(boolean maskingEnabled) {
        this.maskingEnabled = maskingEnabled;
    }

    public List<String> getMaskHeaders() {
        return maskHeaders;
    }

    public void setMaskHeaders(List<String> maskHeaders) {
        this.maskHeaders = maskHeaders == null ? new ArrayList<String>() : maskHeaders;
    }

    public List<String> getMaskKeys() {
        return maskKeys;
    }

    public void setMaskKeys(List<String> maskKeys) {
        this.maskKeys = maskKeys == null ? new ArrayList<String>() : maskKeys;
    }

    public boolean isSkipReplayRequests() {
        return skipReplayRequests;
    }

    public void setSkipReplayRequests(boolean skipReplayRequests) {
        this.skipReplayRequests = skipReplayRequests;
    }

    public String getReplayHeaderName() {
        return replayHeaderName;
    }

    public void setReplayHeaderName(String replayHeaderName) {
        this.replayHeaderName = replayHeaderName;
    }

    public String getReplayHeaderValue() {
        return replayHeaderValue;
    }

    public void setReplayHeaderValue(String replayHeaderValue) {
        this.replayHeaderValue = replayHeaderValue;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /** 根据 JDBC URL 推断驱动类名，未识别返回 null。 */
    public static String guessDriverClassName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String url = jdbcUrl.toLowerCase();
        if (url.startsWith("jdbc:mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (url.startsWith("jdbc:h2")) {
            return "org.h2.Driver";
        }
        if (url.startsWith("jdbc:postgresql")) {
            return "org.postgresql.Driver";
        }
        if (url.startsWith("jdbc:dm")) {
            return "dm.jdbc.driver.DmDriver";
        }
        if (url.startsWith("jdbc:kingbase8")) {
            return "com.kingbase8.Driver";
        }
        return null;
    }

    @Override
    public String toString() {
        return "RecorderSettings{enabled=" + enabled + ", projectCode='" + projectCode + '\''
                + ", jdbcUrl='" + jdbcUrl + '\'' + ", async=" + asyncEnabled
                + ", queueCapacity=" + queueCapacity + ", workers=" + workerThreads
                + ", maxBodySize=" + maxBodySize + '}';
    }
}
