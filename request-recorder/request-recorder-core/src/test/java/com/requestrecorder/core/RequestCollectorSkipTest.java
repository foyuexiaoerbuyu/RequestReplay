package com.requestrecorder.core;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.model.RequestSnapshot;
import com.requestrecorder.core.service.RequestCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采集判定逻辑单元测试：排除路径、静态资源、回放头、WebSocket、SSE。
 */
class RequestCollectorSkipTest {

    private RecorderSettings baseSettings() {
        RecorderSettings settings = new RecorderSettings();
        settings.setProjectCode("ut-project");
        settings.setExcludePaths(new ArrayList<String>(Arrays.asList("/actuator/**", "/error", "/favicon.ico")));
        settings.setRecordStaticResources(false);
        settings.setSkipReplayRequests(true);
        settings.setReplayHeaderName("X-Request-Replay");
        settings.setReplayHeaderValue("true");
        settings.setSkipWebSocket(true);
        settings.setSkipSse(true);
        return settings;
    }

    private RequestCollector collector(RecorderSettings settings) {
        return new RequestCollector(settings, null);
    }

    private RequestSnapshot request(String uri, Map<String, List<String>> headers) {
        RequestSnapshot snapshot = new RequestSnapshot();
        snapshot.setMethod("GET");
        snapshot.setUri(uri);
        snapshot.setUrl("http://localhost" + uri);
        snapshot.setHeaders(headers);
        return snapshot;
    }

    private Map<String, List<String>> headers(String name, String value) {
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        headers.put(name, Arrays.asList(value));
        return headers;
    }

    @Test
    @DisplayName("普通业务请求必须采集")
    void normalRequestIsRecorded() {
        assertFalse(collector(baseSettings()).shouldSkip(request("/api/orders/1", new LinkedHashMap<String, List<String>>())));
    }

    @Test
    @DisplayName("排除路径（Ant 风格）不采集")
    void excludePathsAreSkipped() {
        RequestCollector collector = collector(baseSettings());
        assertTrue(collector.shouldSkip(request("/actuator/health", new LinkedHashMap<String, List<String>>())));
        assertTrue(collector.shouldSkip(request("/error", new LinkedHashMap<String, List<String>>())));
    }

    @Test
    @DisplayName("静态资源默认不采集，开启后采集")
    void staticResourcesRespectSwitch() {
        RequestCollector collector = collector(baseSettings());
        assertTrue(collector.shouldSkip(request("/static/app.js", new LinkedHashMap<String, List<String>>())));

        RecorderSettings settings = baseSettings();
        settings.setRecordStaticResources(true);
        assertFalse(collector(settings).shouldSkip(request("/static/app.js", new LinkedHashMap<String, List<String>>())));
    }

    @Test
    @DisplayName("回放请求头命中的请求不采集，避免回放产生新记录导致死循环")
    void replayHeaderIsSkipped() {
        RequestCollector collector = collector(baseSettings());
        assertTrue(collector.shouldSkip(request("/api/orders", headers("X-Request-Replay", "true"))));
        // 头部名称大小写不敏感
        assertTrue(collector.shouldSkip(request("/api/orders", headers("x-request-replay", "TRUE"))));
        // 非回放值正常采集
        assertFalse(collector.shouldSkip(request("/api/orders", headers("X-Request-Replay", "false"))));

        RecorderSettings settings = baseSettings();
        settings.setSkipReplayRequests(false);
        assertFalse(collector(settings).shouldSkip(request("/api/orders", headers("X-Request-Replay", "true"))));
    }

    @Test
    @DisplayName("WebSocket 升级请求不采集")
    void websocketUpgradeIsSkipped() {
        RequestCollector collector = collector(baseSettings());
        assertTrue(collector.shouldSkip(request("/ws/chat", headers("Upgrade", "websocket"))));
    }

    @Test
    @DisplayName("SSE 请求不采集")
    void sseRequestIsSkipped() {
        RequestCollector collector = collector(baseSettings());
        assertTrue(collector.shouldSkip(request("/api/stream", headers("Accept", "text/event-stream"))));
    }
}
