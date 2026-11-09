package com.requestrecorder.core.database;

import com.requestrecorder.core.config.RecorderSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 内置简易连接实现：不依赖任何连接池，适合业务工程未引入 JDBC / HikariCP 的场景。
 * <p>
 * 采集为异步低频写入，连接开销可接受；数据库不可用时仅抛 SQLException 由上层重试。
 */
final class DriverManagerConnectionProvider implements ConnectionProvider {

    private static final Logger log = LoggerFactory.getLogger(DriverManagerConnectionProvider.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Properties properties = new Properties();

    DriverManagerConnectionProvider(RecorderSettings settings) {
        this.jdbcUrl = settings.getJdbcUrl();
        this.username = settings.getUsername();
        this.password = settings.getPassword();
        if (username != null) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        String driver = settings.getDriverClassName();
        if (driver == null || driver.trim().isEmpty()) {
            driver = RecorderSettings.guessDriverClassName(jdbcUrl);
        }
        if (driver != null && !driver.trim().isEmpty()) {
            try {
                Class.forName(driver.trim());
            } catch (ClassNotFoundException e) {
                log.warn("RequestRecorder 驱动类未找到（依赖 JDBC4 自动注册）: {}", driver);
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    @Override
    public void close() {
        // 无连接池，无需释放
    }
}
