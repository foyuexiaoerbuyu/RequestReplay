package com.requestrecorder.core.util;

import java.util.List;
import java.util.Map;

/**
 * 客户端 IP 解析：优先取反向代理头，其次回落容器 remoteAddr。
 */
public final class ClientIps {

    private static final String[] HEADERS = new String[]{
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP"
    };

    private ClientIps() {
    }

    public static String resolve(Map<String, List<String>> headers, String remoteAddr) {
        if (headers != null) {
            for (String header : HEADERS) {
                List<String> values = lookup(headers, header);
                if (values == null || values.isEmpty()) {
                    continue;
                }
                for (String value : values) {
                    if (value == null) {
                        continue;
                    }
                    String candidate = value.split(",")[0].trim();
                    if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return remoteAddr;
    }

    private static List<String> lookup(Map<String, List<String>> headers, String name) {
        List<String> direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
