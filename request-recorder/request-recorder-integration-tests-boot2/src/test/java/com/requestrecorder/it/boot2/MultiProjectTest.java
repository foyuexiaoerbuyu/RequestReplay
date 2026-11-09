package com.requestrecorder.it.boot2;

import com.requestrecorder.core.service.RecorderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多项目测试：两个不同 project-code 的应用实例写入同一个采集库，记录互不混淆。
 */
@DisplayName("多项目共用同一采集数据库（Boot 2）")
class MultiProjectTest {

    private static final String JDBC_URL = "jdbc:h2:mem:multi_project_boot2;DB_CLOSE_DELAY=-1;MODE=MySQL";

    @Test
    @DisplayName("两个项目写入同一数据库，project_code 不混淆")
    void twoProjectsShareDatabaseWithoutMixing() throws Exception {
        ConfigurableApplicationContext projectA = start("project-a", "项目 A");
        ConfigurableApplicationContext projectB = start("project-b", "项目 B");
        try {
            RestTemplate http = new RestTemplate();
            assertOk(http.getForEntity(url(projectA, "/api/echo?name=a"), String.class));
            assertOk(http.getForEntity(url(projectB, "/api/echo?name=b"), String.class));

            assertTrue(projectA.getBean(RecorderService.class).awaitDrain(5000L), "项目 A 采集队列未排空");
            assertTrue(projectB.getBean(RecorderService.class).awaitDrain(5000L), "项目 B 采集队列未排空");

            List<String[]> rows = queryAll();
            assertEquals(2, rows.size(), "两个项目应各写入一条记录，实际: " + describe(rows));

            Map<String, String> projectCodeByQuery = new LinkedHashMap<String, String>();
            for (String[] row : rows) {
                projectCodeByQuery.put(row[2], row[0]);
            }
            assertEquals("project-a", projectCodeByQuery.get("name=a"), "name=a 的记录 project_code 错误: " + describe(rows));
            assertEquals("project-b", projectCodeByQuery.get("name=b"), "name=b 的记录 project_code 错误: " + describe(rows));
            assertEquals(Integer.valueOf(1), countByProject("project-a"), "project-a 记录数错误");
            assertEquals(Integer.valueOf(1), countByProject("project-b"), "project-b 记录数错误");
        } finally {
            projectA.close();
            projectB.close();
        }
    }

    private ConfigurableApplicationContext start(String projectCode, String projectName) {
        // 使用命令行参数注入，优先级高于工程内 application.properties
        List<String> args = new ArrayList<String>();
        args.add("--server.port=0");
        args.add("--spring.application.name=" + projectCode);
        args.add("--request-recorder.enabled=true");
        args.add("--request-recorder.project-code=" + projectCode);
        args.add("--request-recorder.project-name=" + projectName);
        args.add("--request-recorder.auto-init-schema=true");
        args.add("--request-recorder.exclude-paths=/skip/**");
        args.add("--request-recorder.database.jdbc-url=" + JDBC_URL);
        args.add("--request-recorder.database.driver-class-name=org.h2.Driver");
        args.add("--request-recorder.database.username=sa");
        args.add("--request-recorder.database.password=");
        args.add("--request-recorder.async.enabled=true");
        args.add("--request-recorder.async.workers=2");
        args.add("--request-recorder.async.queue-capacity=200");
        return new SpringApplicationBuilder(ItApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args.toArray(new String[0]));
    }

    private String url(ConfigurableApplicationContext context, String path) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port, "未能获取应用端口");
        return "http://127.0.0.1:" + port + path;
    }

    private void assertOk(ResponseEntity<String> response) {
        assertEquals(200, response.getStatusCodeValue(), "业务接口响应异常: " + response.getBody());
    }

    private List<String[]> queryAll() throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT project_code, uri, query_params FROM http_request_record ORDER BY id ASC")) {
            while (rs.next()) {
                rows.add(new String[]{rs.getString(1), rs.getString(2), normalize(rs.getString(3))});
            }
        }
        return rows;
    }

    private Integer countByProject(String projectCode) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             java.sql.PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM http_request_record WHERE project_code=?")) {
            ps.setString(1, projectCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String normalize(String queryParams) {
        if (queryParams == null) {
            return "";
        }
        String value = queryParams.replace(" ", "").replace("\"", "");
        return value.startsWith("?") ? value.substring(1) : value;
    }

    private String describe(List<String[]> rows) {
        StringBuilder builder = new StringBuilder();
        for (String[] row : rows) {
            builder.append("[project_code=").append(row[0])
                    .append(", uri=").append(row[1])
                    .append(", query_params=").append(row[2]).append("] ");
        }
        return builder.toString();
    }
}
