package com.requestrecorder.it.boot3;

import com.requestrecorder.core.database.RecorderDataSource;
import com.requestrecorder.core.service.RecorderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 8.0 落库冒烟测试（默认跳过，仅在显式开启时执行）。
 * <p>
 * 开启方式：
 * <pre>
 * mvn -pl request-recorder-integration-tests-boot3 test \
 *     -Drequest-recorder.it.mysql=true \
 *     -Drequest-recorder.it.mysql.host=127.0.0.1 \
 *     -Drequest-recorder.it.mysql.port=3306 \
 *     -Drequest-recorder.it.mysql.database=request_recorder \
 *     -Drequest-recorder.it.mysql.user=root \
 *     -Drequest-recorder.it.mysql.password=******
 * </pre>
 * 测试使用一次性的 project-code，结束前只清理该 project-code 自身写入的数据，
 * 不触碰库内既有业务记录。
 */
@SpringBootTest(classes = ItApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "request-recorder.it.mysql", matches = "true")
class MySqlSmokeTest {

    /** 一次性项目编码，避免与库内既有数据混淆。 */
    private static final String PROJECT_CODE = "compat-smoke-" + System.currentTimeMillis();

    private static final String HOST = setting("host", "127.0.0.1");
    private static final String PORT = setting("port", "3306");
    private static final String DATABASE = setting("database", "request_recorder");
    private static final String USER = setting("user", "root");
    private static final String PASSWORD = setting("password", "");

    /**
     * 读取连接参数：优先系统属性 {@code -Drequest-recorder.it.mysql.xxx}，
     * 其次环境变量 {@code RR_IT_MYSQL_XXX}（避免密码等特殊字符在命令行转义问题）。
     */
    private static String setting(String name, String defaultValue) {
        String value = System.getProperty("request-recorder.it.mysql." + name);
        if (value == null || value.isEmpty()) {
            value = System.getenv("RR_IT_MYSQL_" + name.toUpperCase(java.util.Locale.ROOT));
        }
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RecorderService service;

    @Autowired
    private RecorderDataSource dataSource;

    private static String jdbcUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    @DynamicPropertySource
    static void registerRecorderProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.application.name", () -> PROJECT_CODE);
        registry.add("request-recorder.enabled", () -> "true");
        registry.add("request-recorder.project-code", () -> PROJECT_CODE);
        registry.add("request-recorder.project-name", () -> "RequestRecorder MySQL 冒烟");
        registry.add("request-recorder.auto-init-schema", () -> "true");
        registry.add("request-recorder.max-body-size", () -> "256");
        registry.add("request-recorder.exclude-paths", () -> "/skip/**");
        registry.add("request-recorder.database.jdbc-url", MySqlSmokeTest::jdbcUrl);
        registry.add("request-recorder.database.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("request-recorder.database.username", () -> USER);
        registry.add("request-recorder.database.password", () -> PASSWORD);
        registry.add("request-recorder.async.enabled", () -> "true");
        registry.add("request-recorder.async.workers", () -> "2");
        registry.add("request-recorder.async.queue-capacity", () -> "200");
    }

    @Test
    @DisplayName("真实 MySQL 8.0 写入成功，且 Python GUI 的查询语句可直接读取")
    void writesToRealMySqlAndReadableByGui() throws Exception {
        try {
            ResponseEntity<String> get = rest.getForEntity("/api/echo?name=mysql", String.class);
            assertEquals(200, get.getStatusCodeValue());
            assertEquals("echo:mysql", get.getBody());

            assertTrue(service.awaitDrain(10000L), "采集队列未在超时前排空");

            try (Connection connection = dataSource.getConnection()) {
                // 1. 项目表注册
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT project_name FROM http_request_project WHERE project_code=?")) {
                    ps.setString(1, PROJECT_CODE);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "项目未注册到 http_request_project");
                        assertEquals("RequestRecorder MySQL 冒烟", rs.getString("project_name"));
                    }
                }

                // 2. GUI 列表查询（与 python/main.py 完全一致的字段集）
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id,project_code,request_time,method,uri,response_status,duration_ms,client_ip "
                                + "FROM http_request_record WHERE project_code=? ORDER BY id DESC")) {
                    ps.setString(1, PROJECT_CODE);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "GUI 列表查询取不到采集记录");
                        assertTrue(rs.getLong("id") > 0L);
                        assertEquals(PROJECT_CODE, rs.getString("project_code"));
                        assertNotNull(rs.getTimestamp("request_time"));
                        assertEquals("GET", rs.getString("method"));
                        assertEquals("/api/echo", rs.getString("uri"));
                        assertEquals(200, rs.getInt("response_status"));
                        assertTrue(rs.getLong("duration_ms") >= 0L);
                        assertNotNull(rs.getString("client_ip"));
                        assertFalse(rs.next(), "同一次请求不应产生多条记录");
                    }
                }

                // 3. GUI 详情查询（SELECT * 取全字段，校验快照内容可用）
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM http_request_record WHERE project_code=? ORDER BY id DESC")) {
                    ps.setString(1, PROJECT_CODE);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "GUI 详情查询取不到记录");
                        assertEquals("echo:mysql", rs.getString("response_body"));
                        assertNotNull(rs.getString("request_id"));
                        assertTrue(rs.getString("url").contains("/api/echo?name=mysql"), rs.getString("url"));
                        assertNotNull(rs.getString("request_headers"));
                        assertNotNull(rs.getString("response_headers"));
                    }
                }
            }
        } finally {
            cleanup();
        }
    }

    /** 只清理本测试写入的一次性 project-code 数据，不触碰既有业务记录。 */
    private void cleanup() {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM http_request_record WHERE project_code=?")) {
                ps.setString(1, PROJECT_CODE);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM http_request_project WHERE project_code=?")) {
                ps.setString(1, PROJECT_CODE);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[MySqlSmokeTest] 清理测试数据失败：" + e.getMessage());
        }
    }
}
