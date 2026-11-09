package com.requestrecorder.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Servlet 容器无关的响应快照。
 */
public class ResponseSnapshot {

    private int status;
    private Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    private byte[] body;
    private String contentType;
    private String characterEncoding;
    private long size;
    private boolean bodyTruncated;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isBodyTruncated() {
        return bodyTruncated;
    }

    public void setBodyTruncated(boolean bodyTruncated) {
        this.bodyTruncated = bodyTruncated;
    }
}
