package com.requestrecorder.core.service;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.model.RecordData;
import com.requestrecorder.core.model.RequestSnapshot;
import com.requestrecorder.core.model.ResponseSnapshot;
import com.requestrecorder.core.queue.AsyncRecordQueue;
import com.requestrecorder.core.serializer.SensitiveMasker;
import com.requestrecorder.core.util.AntPathMatcher;
import com.requestrecorder.core.util.ClientIps;
import com.requestrecorder.core.util.ContentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采集核心逻辑：判定是否采集、组装 {@link RecordData}、提交到异步队列。
 * <p>
 * 与 Servlet 容器无关，boot2 / boot3 的 filter 只需要把容器对象转成快照后调用本类。
 */
public final class RequestCollector {

    private static final Logger log = LoggerFactory.getLogger(RequestCollector.class);

    private final RecorderSettings settings;
    private final AsyncRecordQueue queue;

    public RequestCollector(RecorderSettings settings, AsyncRecordQueue queue) {
        this.settings = settings;
        this.queue = queue;
    }

    /**
     * 是否跳过采集（排除路径、回放请求头、非文本静态资源等）。
     */
    public boolean shouldSkip(RequestSnapshot request) {
        if (request == null) {
            return true;
        }
        String uri = request.getUri();
        if (AntPathMatcher.matchAny(settings.getExcludePaths(), uri)) {
            return true;
        }
        if (!settings.isRecordStaticResources() && isStaticResource(uri)) {
            return true;
        }
        if (settings.isSkipReplayRequests() && hasReplayHeader(request.getHeaders())) {
            return true;
        }
        if (settings.isSkipWebSocket() && isWebSocketUpgrade(request.getHeaders())) {
            return true;
        }
        if (settings.isSkipSse() && isSseRequest(request.getHeaders())) {
            return true;
        }
        return false;
    }

    /** 组装采集记录（不做脱敏以外的任何写操作，可在业务线程直接调用）。 */
    public RecordData build(RequestSnapshot request, ResponseSnapshot response,
                            long startTime, String exceptionMessage) {
        RecordData data = new RecordData();
        data.setProjectCode(settings.getProjectCode());
        data.setProjectName(settings.resolveProjectName());
        data.setRequestId(UUID.randomUUID().toString());
        data.setRequestTime(startTime);
        data.setMethod(request.getMethod());
        data.setUrl(request.getUrl());
        data.setUri(request.getUri());
        data.setQueryParams(request.getQueryString());
        data.setPathParams(com.requestrecorder.core.serializer.JsonSerializer.toJson(
                request.getPathParams(), "{}"));
        data.setRequestHeaders(SensitiveMasker.headersToJson(request.getHeaders(), settings));

        String requestCharsetName = request.getCharacterEncoding();
        Charset requestCharset = ContentTypes.resolveCharset(request.getContentType(), requestCharsetName,
                Charset.forName("UTF-8"));
        data.setRequestBody(renderBody(request.getBody(), request.getContentType(), requestCharset,
                requestBodyPlaceholder(request)));
        data.setRequestTruncated(request.isBodyTruncated() || exceedsLimit(request.getBody()));

        data.setClientIp(ClientIps.resolve(request.getHeaders(), request.getClientIp()));
        data.setUserAgent(firstHeader(request.getHeaders(), "User-Agent"));

        data.setResponseStatus(response.getStatus());
        data.setResponseHeaders(SensitiveMasker.headersToJson(response.getHeaders(), settings));
        Charset responseCharset = ContentTypes.resolveResponseCharset(response.getContentType(),
                response.getCharacterEncoding(), requestCharset);
        data.setResponseBody(renderBody(response.getBody(), response.getContentType(), responseCharset,
                responseBodyPlaceholder(response)));
        data.setResponseSize(response.getSize());
        data.setResponseTruncated(response.isBodyTruncated() || exceedsLimit(response.getBody()));
        data.setDurationMs(Math.max(0L, System.currentTimeMillis() - startTime));
        data.setExceptionMessage(exceptionMessage);
        return data;
    }

    /**
     * 采集一条请求：判定 → 组装 → 入队。任何异常都被吞掉，绝不影响业务。
     *
     * @return true 表示已提交
     */
    public boolean record(RequestSnapshot request, ResponseSnapshot response,
                          long startTime, String exceptionMessage) {
        AsyncRecordQueue target = queue;
        if (target != null) {
            target.captureBegin();
        }
        try {
            if (shouldSkip(request)) {
                return false;
            }
            RecordData data = build(request, response, startTime, exceptionMessage);
            return target != null && target.submit(data);
        } catch (Throwable t) {
            log.warn("RequestRecorder 采集异常（已忽略，不影响业务）: {}", t.toString());
            return false;
        } finally {
            if (target != null) {
                target.captureEnd();
            }
        }
    }

    public RecorderSettings getSettings() {
        return settings;
    }

    private String renderBody(byte[] body, String contentType, Charset charset, String nonTextPlaceholder) {
        if (body == null || body.length == 0) {
            return "";
        }
        if (!ContentTypes.isTextual(contentType)) {
            return nonTextPlaceholder;
        }
        int limit = settings.getMaxBodySize() > 0 ? settings.getMaxBodySize() : body.length;
        int length = Math.min(body.length, limit);
        String text = new String(body, 0, length, charset);
        return SensitiveMasker.maskBody(text, contentType, settings);
    }

    private boolean exceedsLimit(byte[] body) {
        return body != null && settings.getMaxBodySize() > 0 && body.length > settings.getMaxBodySize();
    }

    private String requestBodyPlaceholder(RequestSnapshot request) {
        String contentType = request.getContentType();
        if (!settings.isRecordMultipart() && contentType != null
                && contentType.toLowerCase().startsWith("multipart/")) {
            return "[multipart request body skipped]";
        }
        return "[non-text request body skipped]";
    }

    private String responseBodyPlaceholder(ResponseSnapshot response) {
        String contentType = response.getContentType();
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            if (settings.isSkipSse() && lower.startsWith("text/event-stream")) {
                return "[sse response body skipped]";
            }
        }
        if (settings.isSkipFileDownload()) {
            String disposition = firstHeader(response.getHeaders(), "Content-Disposition");
            if (disposition != null && disposition.toLowerCase().contains("attachment")) {
                return "[file download body skipped]";
            }
        }
        return "[non-text response body skipped]";
    }

    private boolean isWebSocketUpgrade(Map<String, List<String>> headers) {
        String upgrade = firstHeader(headers, "Upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
    }

    private boolean isSseRequest(Map<String, List<String>> headers) {
        String accept = firstHeader(headers, "Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }

    private boolean hasReplayHeader(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty() || settings.getReplayHeaderName() == null) {
            return false;
        }
        List<String> values = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(settings.getReplayHeaderName())) {
                values = entry.getValue();
                break;
            }
        }
        if (values == null || values.isEmpty()) {
            return false;
        }
        String expected = settings.getReplayHeaderValue();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (expected == null || expected.trim().isEmpty() || expected.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                return values == null || values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }

    private static boolean isStaticResource(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".ico") || lower.endsWith(".svg") || lower.endsWith(".woff")
                || lower.endsWith(".woff2") || lower.endsWith(".ttf") || lower.endsWith(".map");
    }
}
