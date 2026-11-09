package com.requestrecorder.core.model;

/**
 * 单次 HTTP 请求的采集记录，字段与 http_request_record 表列一一对应。
 * 纯 POJO，必须保留标准 getter/setter，禁止使用 public 字段。
 */
public class RecordData {

    private String projectCode;
    private String projectName;
    private String requestId;
    private long requestTime;
    private String method;
    private String url;
    private String uri;
    private String queryParams;
    private String pathParams;
    private String requestHeaders;
    private String requestBody;
    private String clientIp;
    private String userAgent;
    private int responseStatus;
    private String responseHeaders;
    private String responseBody;
    private long durationMs;
    private long responseSize;
    private String exceptionMessage;
    private boolean requestTruncated;
    private boolean responseTruncated;

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

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

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getPathParams() {
        return pathParams;
    }

    public void setPathParams(String pathParams) {
        this.pathParams = pathParams;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
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

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public long getResponseSize() {
        return responseSize;
    }

    public void setResponseSize(long responseSize) {
        this.responseSize = responseSize;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public boolean isRequestTruncated() {
        return requestTruncated;
    }

    public void setRequestTruncated(boolean requestTruncated) {
        this.requestTruncated = requestTruncated;
    }

    public boolean isResponseTruncated() {
        return responseTruncated;
    }

    public void setResponseTruncated(boolean responseTruncated) {
        this.responseTruncated = responseTruncated;
    }

    @Override
    public String toString() {
        return "RecordData{" + method + " " + uri + ", status=" + responseStatus
                + ", project=" + projectCode + ", requestId=" + requestId + "}";
    }
}
