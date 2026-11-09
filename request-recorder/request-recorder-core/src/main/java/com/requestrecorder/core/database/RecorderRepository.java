package com.requestrecorder.core.database;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.model.ProjectInfo;
import com.requestrecorder.core.model.RecordData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * 采集数据持久化，只操作采集组件专属表。
 */
public class RecorderRepository {

    private static final Logger log = LoggerFactory.getLogger(RecorderRepository.class);

    private static final String INSERT_SQL =
            "INSERT INTO http_request_record("
                    + "project_code,project_name,request_id,request_time,method,url,uri,query_params,path_params,"
                    + "request_headers,request_body,client_ip,user_agent,response_status,response_headers,response_body,"
                    + "duration_ms,response_size,exception_message,request_truncated,response_truncated"
                    + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_PROJECT_SQL =
            "INSERT INTO http_request_project(project_code,project_name,description) VALUES(?,?,?)";

    private static final String UPDATE_PROJECT_SQL =
            "UPDATE http_request_project SET project_name=?, "
                    + "description=COALESCE(NULLIF(?, ''), description), "
                    + "updated_at=CURRENT_TIMESTAMP WHERE project_code=?";

    private final RecorderDataSource dataSource;
    private final RecorderSettings settings;

    public RecorderRepository(RecorderDataSource dataSource, RecorderSettings settings) {
        this.dataSource = dataSource;
        this.settings = settings;
    }

    /** 单条采集记录落库。失败抛出 SQLException，由队列层负责重试与计数。 */
    public void insert(RecordData record) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            int i = 1;
            ps.setString(i++, truncate(record.getProjectCode(), 100));
            ps.setString(i++, truncate(record.getProjectName(), 200));
            ps.setString(i++, truncate(record.getRequestId(), 64));
            ps.setTimestamp(i++, new Timestamp(record.getRequestTime()));
            ps.setString(i++, truncate(record.getMethod(), 20));
            ps.setString(i++, record.getUrl());
            ps.setString(i++, truncate(record.getUri(), 1000));
            ps.setString(i++, record.getQueryParams());
            ps.setString(i++, record.getPathParams());
            ps.setString(i++, record.getRequestHeaders());
            ps.setString(i++, record.getRequestBody());
            ps.setString(i++, truncate(record.getClientIp(), 64));
            ps.setString(i++, truncate(record.getUserAgent(), 1000));
            ps.setInt(i++, record.getResponseStatus());
            ps.setString(i++, record.getResponseHeaders());
            ps.setString(i++, record.getResponseBody());
            ps.setLong(i++, record.getDurationMs());
            ps.setLong(i++, record.getResponseSize());
            ps.setString(i++, truncate(record.getExceptionMessage(), 4000));
            ps.setBoolean(i++, record.isRequestTruncated());
            ps.setBoolean(i, record.isResponseTruncated());
            ps.executeUpdate();
        }
    }

    /**
     * 注册（或更新）项目信息，幂等。
     * <p>
     * 采用「先 UPDATE，未命中再 INSERT」的通用写法，不依赖 MySQL 的 {@code ON DUPLICATE KEY}，
     * 因此可在 MySQL / H2 / 人大金仓 / 达梦等库上一致工作；并发插入冲突时再回退一次 UPDATE。
     */
    public void upsertProject(ProjectInfo info) throws SQLException {
        String code = truncate(info.getProjectCode(), 100);
        String name = truncate(info.getProjectName(), 200);
        String description = truncate(info.getDescription(), 500);
        if (updateProject(code, name, description)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_PROJECT_SQL)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            // 并发下可能已被其他实例插入，重试更新
            if (!updateProject(code, name, description)) {
                throw e;
            }
        }
    }

    private boolean updateProject(String code, String name, String description) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_PROJECT_SQL)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, code);
            return ps.executeUpdate() > 0;
        }
    }

    /** 判断采集表是否存在。 */
    public boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, tableName,
                     new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            ps.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 自动建表（{@code request-recorder.auto-init-schema=true} 时启用），
     * 与 {@code sql/schema.sql}（MySQL 8.0）保持一致。
     * <p>
     * 优先执行 MySQL 原生 DDL；失败时回退通用 DDL（适配 H2 等测试数据库）。
     */
    public void initSchema() throws SQLException {
        if (isMySqlFamily()) {
            try {
                executeAll(MYSQL_DDL);
                log.info("RequestRecorder 采集表已就绪（MySQL 方言）");
                return;
            } catch (SQLException e) {
                log.warn("RequestRecorder MySQL 方言建表失败，回退通用 DDL: {}", e.toString());
            }
        }
        executeAll(GENERIC_DDL);
        createIndexesQuietly();
        log.info("RequestRecorder 采集表已就绪（通用方言）");
    }

    /** 探测数据库是否为 MySQL / MariaDB 家族，决定使用哪套建表 DDL。 */
    private boolean isMySqlFamily() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null) {
                return false;
            }
            String lower = product.toLowerCase();
            return lower.contains("mysql") || lower.contains("mariadb");
        }
    }

    private static final String[] MYSQL_DDL = new String[]{
            "CREATE TABLE IF NOT EXISTS http_request_project("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, project_code VARCHAR(100) NOT NULL UNIQUE,"
                    + "project_name VARCHAR(200) NOT NULL, description VARCHAR(500),"
                    + "created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                    + "updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS http_request_record("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, project_code VARCHAR(100) NOT NULL,"
                    + "project_name VARCHAR(200), request_id VARCHAR(64) NOT NULL, request_time DATETIME(3) NOT NULL,"
                    + "method VARCHAR(20) NOT NULL, url TEXT NOT NULL, uri VARCHAR(1000), query_params LONGTEXT,"
                    + "path_params LONGTEXT, request_headers LONGTEXT, request_body LONGTEXT, client_ip VARCHAR(64),"
                    + "user_agent VARCHAR(1000), response_status INT, response_headers LONGTEXT, response_body LONGTEXT,"
                    + "duration_ms BIGINT, response_size BIGINT, exception_message TEXT,"
                    + "request_truncated TINYINT(1) DEFAULT 0, response_truncated TINYINT(1) DEFAULT 0,"
                    + "created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),"
                    + "KEY idx_project_time(project_code,request_time), KEY idx_method_status(method,response_status),"
                    + "KEY idx_uri(uri(191)), KEY idx_request_id(request_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS http_replay_history("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, source_record_id BIGINT, project_code VARCHAR(100) NOT NULL,"
                    + "target_url TEXT NOT NULL, method VARCHAR(20) NOT NULL, replay_time DATETIME(3) NOT NULL,"
                    + "replay_status VARCHAR(30) NOT NULL, response_status INT, response_headers LONGTEXT,"
                    + "response_body LONGTEXT, duration_ms BIGINT, exception_message TEXT,"
                    + "KEY idx_replay_project_time(project_code,replay_time), KEY idx_replay_source(source_record_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    };

    private static final String[] GENERIC_DDL = new String[]{
            "CREATE TABLE IF NOT EXISTS http_request_project("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, project_code VARCHAR(100) NOT NULL,"
                    + "project_name VARCHAR(200) NOT NULL, description VARCHAR(500),"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS http_request_record("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, project_code VARCHAR(100) NOT NULL,"
                    + "project_name VARCHAR(200), request_id VARCHAR(64) NOT NULL, request_time TIMESTAMP NOT NULL,"
                    + "method VARCHAR(20) NOT NULL, url VARCHAR(4000) NOT NULL, uri VARCHAR(1000), query_params TEXT,"
                    + "path_params TEXT, request_headers TEXT, request_body TEXT, client_ip VARCHAR(64),"
                    + "user_agent VARCHAR(1000), response_status INT, response_headers TEXT, response_body TEXT,"
                    + "duration_ms BIGINT, response_size BIGINT, exception_message TEXT,"
                    + "request_truncated BOOLEAN DEFAULT FALSE, response_truncated BOOLEAN DEFAULT FALSE,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS http_replay_history("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, source_record_id BIGINT, project_code VARCHAR(100) NOT NULL,"
                    + "target_url VARCHAR(4000) NOT NULL, method VARCHAR(20) NOT NULL, replay_time TIMESTAMP NOT NULL,"
                    + "replay_status VARCHAR(30) NOT NULL, response_status INT, response_headers TEXT,"
                    + "response_body TEXT, duration_ms BIGINT, exception_message TEXT)"
    };

    private static final String[] GENERIC_INDEXES = new String[]{
            "CREATE INDEX idx_project_time ON http_request_record(project_code, request_time)",
            "CREATE INDEX idx_method_status ON http_request_record(method, response_status)",
            "CREATE INDEX idx_request_id ON http_request_record(request_id)",
            "CREATE INDEX idx_replay_project_time ON http_replay_history(project_code, replay_time)"
    };

    private void executeAll(String[] ddls) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : ddls) {
                statement.execute(ddl);
            }
        }
    }

    private void createIndexesQuietly() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : GENERIC_INDEXES) {
                try {
                    statement.execute(ddl);
                } catch (SQLException ignored) {
                    // 索引已存在或该库不支持，忽略
                }
            }
        } catch (SQLException e) {
            log.debug("RequestRecorder 通用索引创建失败: {}", e.toString());
        }
    }

    public RecorderSettings getSettings() {
        return settings;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
