package com.requestrecorder.it.boot2;

import com.requestrecorder.core.queue.AsyncRecordQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采集库不可用测试：采集数据库连不上时，业务接口仍须正常响应，采集只在内部告警与计数。
 */
@SpringBootTest(classes = ItApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "request-recorder.project-code=it-dbdown",
        "request-recorder.project-name=采集库不可用测试",
        "request-recorder.database.jdbc-url=jdbc:h2:tcp://127.0.0.1:1/it_db_down",
        "request-recorder.database.username=sa",
        "request-recorder.database.password=",
        "request-recorder.database.connection-timeout-ms=500",
        "request-recorder.async.max-retries=0",
        "request-recorder.async.retry-backoff-ms=0"
})
class DatabaseUnavailableTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AsyncRecordQueue queue;

    @Test
    @DisplayName("采集库不可用时业务接口仍正常返回")
    void businessRequestStillWorksWhenRecorderDatabaseDown() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/api/echo?name=dbdown", String.class);
        assertEquals200(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("echo:dbdown"), "业务响应内容必须完整: " + response.getBody());

        queue.awaitDrain(5000L);
        assertTrue(queue.getFailedCount() >= 1L, "采集库不可用时应累计写入失败计数");
    }

    private void assertEquals200(ResponseEntity<String> response) {
        assertTrue(response.getStatusCodeValue() == 200,
                "采集库不可用不得影响业务响应，实际=" + response.getStatusCodeValue());
    }
}
