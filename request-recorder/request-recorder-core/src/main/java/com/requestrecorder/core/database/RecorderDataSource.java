package com.requestrecorder.core.database;

import com.requestrecorder.core.config.RecorderSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 采集组件专属数据库连接，与业务数据源完全隔离，互不影响。
 * <p>
 * 优先使用 HikariCP；业务工程未引入 HikariCP 或 JDBC 依赖时自动回退到内置简易连接，
 * 保证采集 starter 可在任意 Spring Boot 工程中「加上依赖即用」，不会因缺少连接池而启动失败。
 * <p>
 * 数据库短暂不可用时不阻断业务启动，由采集组件内部打印告警并在后续写入时自动重试。
 */
public final class RecorderDataSource implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RecorderDataSource.class);

    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";

    private final ConnectionProvider provider;
    private final String jdbcUrl;

    public RecorderDataSource(RecorderSettings settings) {
        this.jdbcUrl = settings.getJdbcUrl();
        this.provider = createProvider(settings);
        log.info("RequestRecorder 数据源已初始化: url={}, poolSize={}, type={}", maskUrl(jdbcUrl),
                settings.getPoolSize(), provider.getClass().getSimpleName());
    }

    private static ConnectionProvider createProvider(RecorderSettings settings) {
        if (hikariPresent()) {
            try {
                return new HikariConnectionProvider(settings);
            } catch (Throwable t) {
                log.warn("RequestRecorder 初始化 HikariCP 失败，回退内置连接: {}", t.toString());
            }
        } else {
            log.info("RequestRecorder 未检测到 HikariCP，使用内置简易连接（无需业务工程额外依赖）");
        }
        return new DriverManagerConnectionProvider(settings);
    }

    private static boolean hikariPresent() {
        try {
            Class.forName(HIKARI_CLASS, false, RecorderDataSource.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        return provider.getConnection();
    }

    public boolean isHealthy() {
        try (Connection connection = provider.getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (Exception e) {
            log.warn("RequestRecorder 数据库连接不可用: {}", e.toString());
            return false;
        }
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() {
        try {
            provider.close();
            log.info("RequestRecorder 数据源已关闭");
        } catch (Exception e) {
            log.warn("关闭 RequestRecorder 数据源异常: {}", e.toString());
        }
    }

    private static String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password=)[^&]*", "$1******");
    }
}
