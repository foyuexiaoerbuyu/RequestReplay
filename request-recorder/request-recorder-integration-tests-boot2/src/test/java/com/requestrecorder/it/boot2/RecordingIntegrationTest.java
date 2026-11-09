package com.requestrecorder.it.boot2;

import com.requestrecorder.core.database.RecorderDataSource;
import com.requestrecorder.core.service.RecorderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端采集集成测试：真实容器 + 采集过滤器 + 异步写入 + H2 落库。
 */
@SpringBootTest(classes = ItApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordingIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RecorderService service;

    @Autowired
    private RecorderDataSource dataSource;

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM http_request_record");
        }
    }

    private void drain() {
        assertTrue(service.awaitDrain(5000L), "采集队列未在超时前排空");
    }

    private int countByUri(String uri) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM http_request_record WHERE uri=?")) {
            ps.setString(1, uri);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private ResultSet queryOne(Connection connection, String uri) throws SQLException {
        java.sql.PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM http_request_record WHERE uri=? ORDER BY id DESC");
        ps.setString(1, uri);
        ResultSet rs = ps.executeQuery();
        assertTrue(rs.next(), "未采集到 uri=" + uri + " 的记录");
        return rs;
    }

    @Test
    @DisplayName("正常 GET/POST 请求被采集，字段与响应一致")
    void recordsNormalRequests() throws Exception {
        ResponseEntity<String> get = rest.getForEntity("/api/echo?name=abc", String.class);
        assertEquals(200, get.getStatusCodeValue());
        assertEquals("echo:abc", get.getBody());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> post = rest.postForEntity("/api/order",
                new HttpEntity<String>("{\"sku\":\"A-1\",\"qty\":2}", headers), String.class);
        assertEquals(200, post.getStatusCodeValue());

        drain();

        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = queryOne(connection, "/api/echo");
            assertEquals("GET", rs.getString("method"));
            assertEquals(200, rs.getInt("response_status"));
            assertEquals("echo:abc", rs.getString("response_body"));
            assertEquals("it-boot2", rs.getString("project_code"));
            assertTrue(rs.getString("url").contains("/api/echo?name=abc"), rs.getString("url"));

            ResultSet rs2 = queryOne(connection, "/api/order");
            assertEquals("POST", rs2.getString("method"));
            assertTrue(rs2.getString("request_body").contains("A-1"), rs2.getString("request_body"));
            assertEquals(200, rs2.getInt("response_status"));
        }
    }

    @Test
    @DisplayName("异常请求采集到真实 500 与错误响应体")
    void recordsErrorResponse() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/api/boom", String.class);
        assertEquals(500, response.getStatusCodeValue());
        drain();

        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = queryOne(connection, "/api/boom");
            assertEquals(500, rs.getInt("response_status"));
            assertTrue(rs.getString("response_body").contains("boom-message"), rs.getString("response_body"));
        }
    }

    @Test
    @DisplayName("排除路径不产生采集记录")
    void excludedPathIsNotRecorded() throws Exception {
        rest.getForEntity("/skip/ping", String.class);
        drain();
        assertEquals(0, countByUri("/skip/ping"));
    }

    @Test
    @DisplayName("带回放标记头的请求不产生采集记录，回放后记录数不增加")
    void replayRequestIsNotRecorded() throws Exception {
        // 先产生一条正常采集记录作为回放前基线
        rest.getForEntity("/api/echo?name=before", String.class);
        drain();
        int baseline = countByUri("/api/echo");
        assertEquals(1, baseline, "回放前应有 1 条基线记录");

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-Replay", "true");
        rest.exchange("/api/echo?name=replay", HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
        drain();

        assertEquals(baseline, countByUri("/api/echo"), "回放请求不应新增采集记录");
    }

    @Test
    @DisplayName("响应体超限时截断并标记 response_truncated")
    void largeResponseIsTruncated() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/api/large", String.class);
        assertEquals(200, response.getStatusCodeValue());
        drain();

        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = queryOne(connection, "/api/large");
            assertTrue(rs.getBoolean("response_truncated"), "应标记为截断");
            String body = rs.getString("response_body");
            assertTrue(body != null && body.length() <= 256, "落库响应体应为截断后内容，长度: "
                    + (body == null ? "null" : body.length()));
            assertTrue(rs.getLong("response_size") >= 1024L, "应记录原始响应大小");
        }
    }

    @Test
    @DisplayName("敏感请求头落库前脱敏")
    void sensitiveHeaderIsMasked() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer secret-token-value");
        rest.exchange("/api/echo", HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
        drain();

        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = queryOne(connection, "/api/echo");
            String requestHeaders = rs.getString("request_headers");
            assertTrue(requestHeaders != null && requestHeaders.contains("text/plain"), requestHeaders);
            assertFalse(requestHeaders.contains("secret-token-value"), "敏感头未脱敏: " + requestHeaders);
        }
    }
}
