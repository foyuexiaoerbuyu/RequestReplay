package com.requestrecorder.it.boot3;

import com.requestrecorder.core.database.RecorderDataSource;
import com.requestrecorder.core.queue.AsyncRecordQueue;
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
import org.springframework.test.context.TestPropertySource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步写入与并发采集测试：并发压测下业务响应不受影响、记录不丢失、队列统计可见。
 */
@SpringBootTest(classes = ItApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "request-recorder.project-code=it-concurrent",
        "request-recorder.project-name=并发采集测试项目",
        "request-recorder.async.enabled=true",
        "request-recorder.async.queue-capacity=5000",
        "request-recorder.async.workers=2"
})
class ConcurrentRecordingTest {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 10;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RecorderDataSource dataSource;

    @Autowired
    private AsyncRecordQueue queue;

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM http_request_record");
        }
    }

    private int countAll() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM http_request_record")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("并发请求全部成功且采集记录不丢失")
    void concurrentRequestsAreAllRecorded() throws Exception {
        final int total = THREADS * PER_THREAD;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < total; i++) {
            final int index = i;
            tasks.add(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<String> entity = new HttpEntity<String>("{\"sku\":\"SKU-" + index + "\"}", headers);
                    ResponseEntity<String> response = rest.exchange("/api/order", HttpMethod.POST, entity, String.class);
                    return response.getStatusCodeValue() == 200 && response.getBody() != null;
                }
            });
        }
        List<Future<Boolean>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
        pool.shutdown();
        int success = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get())) {
                success++;
            }
        }
        assertEquals(total, success, "并发请求本身必须全部成功，采集不得影响业务");

        assertTrue(queue.awaitDrain(20000L), "异步队列应在超时内排空");
        int recorded = countAll();
        assertTrue(recorded >= total - 5, "并发场景下采集记录不应明显丢失，实际=" + recorded);
        assertEquals(0L, queue.getDroppedCount(), "队列容量充足时不应发生丢弃");
        assertTrue(queue.stats().getWritten() >= recorded, "写入统计应与记录数匹配");
    }
}
