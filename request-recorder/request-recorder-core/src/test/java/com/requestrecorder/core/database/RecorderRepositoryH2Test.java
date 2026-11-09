package com.requestrecorder.core.database;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.model.ProjectInfo;
import com.requestrecorder.core.model.RecordData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久化层测试：自动建表、字段映射、项目注册幂等、多项目隔离。
 */
class RecorderRepositoryH2Test {

    private RecorderDataSource dataSource;
    private RecorderRepository repository;

    private RecorderSettings settings(String projectCode) {
        RecorderSettings settings = new RecorderSettings();
        settings.setProjectCode(projectCode);
        settings.setProjectName("单元测试项目");
        settings.setJdbcUrl("jdbc:h2:mem:rr-core-" + projectCode + ";DB_CLOSE_DELAY=-1");
        settings.setDriverClassName("org.h2.Driver");
        settings.setUsername("sa");
        settings.setPassword("");
        return settings;
    }

    @BeforeEach
    void setUp() throws Exception {
        RecorderSettings settings = settings("proj-a");
        dataSource = new RecorderDataSource(settings);
        repository = new RecorderRepository(dataSource, settings);
        repository.initSchema();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM http_request_record");
            statement.execute("DELETE FROM http_request_project");
        }
    }

    private RecordData record(String projectCode) {
        RecordData data = new RecordData();
        data.setProjectCode(projectCode);
        data.setProjectName("单元测试项目");
        data.setRequestId("req-" + projectCode);
        data.setRequestTime(System.currentTimeMillis());
        data.setMethod("POST");
        data.setUrl("http://localhost:8080/api/order?x=1");
        data.setUri("/api/order");
        data.setQueryParams("x=1");
        data.setPathParams("{}");
        data.setRequestHeaders("{\"Content-Type\":\"application/json\"}");
        data.setRequestBody("{\"name\":\"张三\"}");
        data.setClientIp("127.0.0.1");
        data.setUserAgent("junit");
        data.setResponseStatus(200);
        data.setResponseHeaders("{\"Content-Type\":\"application/json\"}");
        data.setResponseBody("{\"ok\":true}");
        data.setDurationMs(12L);
        data.setResponseSize(11L);
        data.setRequestTruncated(false);
        data.setResponseTruncated(true);
        return data;
    }

    @Test
    @DisplayName("自动建表后三张表均存在")
    void initSchemaCreatesTables() throws Exception {
        assertTrue(repository.tableExists("http_request_record"));
        assertTrue(repository.tableExists("http_request_project"));
        assertTrue(repository.tableExists("http_replay_history"));
    }

    @Test
    @DisplayName("采集记录字段完整落库")
    void insertPersistsAllFields() throws Exception {
        repository.insert(record("proj-a"));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT project_code, project_name, method, uri, request_body, response_body, "
                             + "response_status, duration_ms, response_truncated FROM http_request_record")) {
            assertTrue(rs.next(), "应有 1 条记录");
            assertEquals("proj-a", rs.getString("project_code"));
            assertEquals("单元测试项目", rs.getString("project_name"));
            assertEquals("POST", rs.getString("method"));
            assertEquals("/api/order", rs.getString("uri"));
            assertEquals("{\"name\":\"张三\"}", rs.getString("request_body"));
            assertEquals("{\"ok\":true}", rs.getString("response_body"));
            assertEquals(200, rs.getInt("response_status"));
            assertEquals(12L, rs.getLong("duration_ms"));
            assertTrue(rs.getBoolean("response_truncated"));
            assertTrue(!rs.next(), "只应有 1 条记录");
        }
    }

    @Test
    @DisplayName("项目注册幂等：重复注册更新名称，空描述不清空原值")
    void upsertProjectIsIdempotent() throws Exception {
        repository.upsertProject(new ProjectInfo("proj-a", "项目A", "初始描述"));
        repository.upsertProject(new ProjectInfo("proj-a", "项目A-改名", null));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) AS c, MAX(project_name) AS n, MAX(description) AS d FROM http_request_project")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("c"), "重复注册不应产生多行");
            assertEquals("项目A-改名", rs.getString("n"));
            assertEquals("初始描述", rs.getString("d"), "空描述不应清空原有值");
        }
    }

    @Test
    @DisplayName("多项目数据按 project_code 隔离")
    void recordsAreIsolatedByProject() throws Exception {
        repository.insert(record("proj-a"));
        repository.insert(record("proj-b"));
        repository.insert(record("proj-b"));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM http_request_record WHERE project_code=?")) {
            ps.setString(1, "proj-a");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            ps.setString(1, "proj-b");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            ps.setString(1, "proj-not-exist");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("未建表时 tableExists 返回 false 且不抛异常")
    void tableExistsHandlesMissingTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE http_replay_history");
        }
        assertTrue(!repository.tableExists("http_replay_history"));
        assertNull(null);
    }
}
