package com.requestrecorder.core.database;

import com.requestrecorder.core.config.RecorderSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 基于 HikariCP 的连接提供者（业务工程存在 HikariCP 时优先使用）。
 * <p>
 * 该类只在确认 classpath 存在 {@code com.zaxxer.hikari.HikariDataSource} 后才会被加载，
 * 保证缺少 HikariCP 的业务工程不会因类解析失败而启动异常。
 */
final class HikariConnectionProvider implements ConnectionProvider {

    private final HikariDataSource dataSource;

    HikariConnectionProvider(RecorderSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.getJdbcUrl());
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        if (settings.getDriverClassName() != null && !settings.getDriverClassName().trim().isEmpty()) {
            config.setDriverClassName(settings.getDriverClassName().trim());
        }
        config.setMaximumPoolSize(Math.max(1, settings.getPoolSize()));
        config.setMinimumIdle(0);
        config.setConnectionTimeout(Math.max(250L, settings.getConnectionTimeoutMs()));
        config.setValidationTimeout(Math.max(250L, settings.getValidationTimeoutMs()));
        config.setIdleTimeout(60000L);
        config.setMaxLifetime(1800000L);
        config.setPoolName("request-recorder-pool");
        // 数据库不可用时不阻断应用启动，写入阶段再重试
        config.setInitializationFailTimeout(-1L);
        config.setRegisterMbeans(false);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
