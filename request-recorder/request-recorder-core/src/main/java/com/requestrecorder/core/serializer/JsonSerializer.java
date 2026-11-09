package com.requestrecorder.core.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化工具，所有异常均吞掉并回落安全值，绝不影响业务请求。
 */
public final class JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSerializer() {
    }

    public static String toJson(Object value, String fallback) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static JsonNode readTree(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
