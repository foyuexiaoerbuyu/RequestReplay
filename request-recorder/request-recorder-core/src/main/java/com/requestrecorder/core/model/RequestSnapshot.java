package com.requestrecorder.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Servlet 容器无关的请求快照，由 boot2 / boot3 filter 负责从容器对象转换而来。
 * core 只依赖该快照，因此 core 不引入任何 javax.servlet / jakarta.servlet 依赖。
 */
public class RequestSnapshot {

    private String method;
    private String url;
    private String uri;
    private String queryString;
    private Map<String, String> pathParams = new LinkedHashMap<String, String>();
    private Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    private byte[] body;
    private String contentType;
    private String characterEncoding;
    private String clientIp;
    private String userAgent;
    private boolean bodyTruncated;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public void setPathParams(Map<String, String> pathParams) {
        this.pathParams = pathParams == null ? new LinkedHashMap<String, String>() : pathParams;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers == null ? new LinkedHashMap<String, List<String>>() : headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isBodyTruncated() {
        return bodyTruncated;
    }

    public void setBodyTruncated(boolean bodyTruncated) {
        this.bodyTruncated = bodyTruncated;
    }
}
