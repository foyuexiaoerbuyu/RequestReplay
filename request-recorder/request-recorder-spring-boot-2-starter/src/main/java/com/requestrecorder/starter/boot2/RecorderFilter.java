package com.requestrecorder.starter.boot2;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.model.RequestSnapshot;
import com.requestrecorder.core.model.ResponseSnapshot;
import com.requestrecorder.core.service.RequestCollector;
import com.requestrecorder.core.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot 2.x（javax.servlet.servlet）采集过滤器。
 * <p>
 * 该 Filter 只负责容器对象到快照的转换，采集逻辑全部委托给 core 的 {@link RequestCollector}，
 * 保证 boot2 / boot3 行为一致。响应采用直通 + 有界缓存，避免大文件下载占用内存。
 */
public class RecorderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RecorderFilter.class);

    private final RecorderSettings settings;
    private final RequestCollector collector;

    public RecorderFilter(RecorderSettings settings, RequestCollector collector) {
        this.settings = settings;
        this.collector = collector;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!settings.isEnabled()) {
            return true;
        }
        if (AntPathMatcher.matchAny(settings.getExcludePaths(), request.getRequestURI())) {
            return true;
        }
        if (settings.isSkipReplayRequests() && isReplayRequest(request)) {
            return true;
        }
        if (settings.isSkipWebSocket() && isWebSocketRequest(request)) {
            return true;
        }
        if (settings.isSkipSse() && isSseRequest(request)) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        int maxBodySize = Math.max(1, settings.getMaxBodySize());
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, maxBodySize);
        BoundedResponseWrapper cachedResponse = new BoundedResponseWrapper(response, maxBodySize);
        String errorMessage = null;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (ServletException e) {
            errorMessage = describe(e);
            throw e;
        } catch (IOException e) {
            errorMessage = describe(e);
            throw e;
        } catch (RuntimeException e) {
            errorMessage = describe(e);
            throw e;
        } finally {
            try {
                // 响应已直通客户端，这里只做刷新，随后异步采集，业务耗时不受影响
                cachedResponse.flushCaptured();
            } catch (Exception e) {
                log.warn("RequestRecorder 刷新响应失败: {}", e.toString());
            }
            try {
                collector.record(buildRequestSnapshot(cachedRequest, maxBodySize),
                        buildResponseSnapshot(cachedResponse, errorMessage, maxBodySize), start, errorMessage);
            } catch (Throwable t) {
                log.warn("RequestRecorder 采集异常（已忽略，不影响业务）: {}", t.toString());
            }
        }
    }

    private RequestSnapshot buildRequestSnapshot(HttpServletRequest request, int maxBodySize) {
        RequestSnapshot snapshot = new RequestSnapshot();
        snapshot.setMethod(request.getMethod());
        snapshot.setUri(request.getRequestURI());
        String query = request.getQueryString();
        snapshot.setQueryString(query);
        snapshot.setUrl(request.getRequestURL() + (query == null || query.isEmpty() ? "" : "?" + query));
        snapshot.setContentType(request.getContentType());
        snapshot.setCharacterEncoding(request.getCharacterEncoding());
        snapshot.setHeaders(toHeaderMap(request));
        snapshot.setClientIp(request.getRemoteAddr());
        snapshot.setPathParams(new LinkedHashMap<String, String>());
        if (request instanceof ContentCachingRequestWrapper) {
            byte[] body = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
            snapshot.setBody(body);
            snapshot.setBodyTruncated(body != null && body.length >= maxBodySize);
        }
        return snapshot;
    }

    private ResponseSnapshot buildResponseSnapshot(BoundedResponseWrapper response, String errorMessage, int maxBodySize) {
        ResponseSnapshot snapshot = new ResponseSnapshot();
        int status = response.getStatus();
        if (errorMessage != null && status < 400) {
            // 异常已抛出、容器尚未写错误页时按真实业务语义记为 500，避免状态码失真
            status = 500;
        }
        snapshot.setStatus(status);
        snapshot.setContentType(response.getContentType());
        snapshot.setCharacterEncoding(response.getCharacterEncoding());
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        Collection<String> names = response.getHeaderNames();
        if (names != null) {
            for (String name : names) {
                headers.put(name, new ArrayList<String>(response.getHeaders(name)));
            }
        }
        snapshot.setHeaders(headers);
        byte[] body = response.getCapturedBody();
        snapshot.setBody(body);
        snapshot.setSize((int) Math.min(Integer.MAX_VALUE, response.getTotalBytes()));
        snapshot.setBodyTruncated(response.isTruncated() || (body != null && body.length >= maxBodySize));
        return snapshot;
    }

    private Map<String, List<String>> toHeaderMap(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        java.util.Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                java.util.Enumeration<String> values = request.getHeaders(name);
                List<String> list = values == null ? new ArrayList<String>() : Collections.list(values);
                headers.put(name, list);
            }
        }
        return headers;
    }

    private boolean isReplayRequest(HttpServletRequest request) {
        String expected = settings.getReplayHeaderValue();
        String actual = request.getHeader(settings.getReplayHeaderName());
        if (actual == null) {
            return false;
        }
        return expected == null || expected.trim().isEmpty() || expected.equalsIgnoreCase(actual.trim());
    }

    private boolean isWebSocketRequest(HttpServletRequest request) {
        String upgrade = request.getHeader("Upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        String simple = t.getClass().getName();
        if (message == null) {
            return simple;
        }
        return message.length() > 1000 ? simple + ": " + message.substring(0, 1000) + "..." : simple + ": " + message;
    }
}
