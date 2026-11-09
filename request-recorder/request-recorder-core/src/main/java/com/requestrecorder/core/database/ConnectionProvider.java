package com.requestrecorder.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 连接提供者抽象：屏蔽 HikariCP 与内置简易连接池的差异。
 */
interface ConnectionProvider {

    Connection getConnection() throws SQLException;

    void close();
}
