package com.requestrecorder.core.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.requestrecorder.core.config.RecorderSettings;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏：请求/响应头按名称脱敏，正文按 JSON 递归 + 键值正则双层脱敏。
 */
public final class SensitiveMasker {

    public static final String MASK = "******";

    /** 超过该长度的正文不再做 JSON 递归解析，退化为正则脱敏，避免拖慢业务请求。 */
    private static final int JSON_PARSE_THRESHOLD = 200_000;

    private SensitiveMasker() {
    }

    /** 请求/响应头转 JSON 字符串，命中敏感头则脱敏。 */
    public static String headersToJson(Map<String, List<String>> headers, RecorderSettings settings) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (headers == null) {
            return "{}";
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            boolean sensitive = settings.isMaskingEnabled() && isMaskedHeader(name, settings.getMaskHeaders());
            if (values == null || values.isEmpty()) {
                out.put(name, sensitive ? MASK : "");
            } else if (values.size() == 1) {
                out.put(name, sensitive ? MASK : values.get(0));
            } else {
                out.put(name, sensitive ? MASK : values);
            }
        }
        return JsonSerializer.toJson(out, "{}");
    }

    /** 正文脱敏，二进制正文请勿调用。 */
    public static String maskBody(String body, String contentType, RecorderSettings settings) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        if (!settings.isMaskingEnabled()) {
            return body;
        }
        List<String> keys = settings.getMaskKeys();
        if (keys == null || keys.isEmpty()) {
            return body;
        }
        String trimmed = body.trim();
        boolean looksJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (looksJson && trimmed.length() <= JSON_PARSE_THRESHOLD) {
            JsonNode node = JsonSerializer.readTree(trimmed);
            if (node != null && (node.isObject() || node.isArray())) {
                maskNode(node, keys);
                return JsonSerializer.toJson(node, maskText(body, keys));
            }
        }
        return maskText(body, keys);
    }

    private static void maskNode(JsonNode node, List<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode child = entry.getValue();
                if (isMaskedKey(entry.getKey(), keys)) {
                    if (child.isValueNode() && !child.isNull()) {
                        obj.set(entry.getKey(), TextNode.valueOf(MASK));
                    } else {
                        obj.set(entry.getKey(), TextNode.valueOf(MASK));
                    }
                } else {
                    maskNode(child, keys);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                maskNode(arr.get(i), keys);
            }
        }
    }

    /** 键值正则脱敏，兼容 application/x-www-form-urlencoded、query 串等非 JSON 文本。 */
    private static String maskText(String text, List<String> keys) {
        String result = text;
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String quoted = Pattern.quote(key.trim());
            Pattern p = Pattern.compile("(?i)([\"']?" + quoted + "[\"']?\\s*[:=]\\s*[\"']?)([^,\"'&;}\\s\\]]+)");
            Matcher m = p.matcher(result);
            if (m.find()) {
                result = m.replaceAll("$1" + MASK);
            }
        }
        return result;
    }

    private static boolean isMaskedHeader(String name, List<String> maskHeaders) {
        if (name == null || maskHeaders == null) {
            return false;
        }
        for (String h : maskHeaders) {
            if (h != null && h.equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean isMaskedKey(String key, List<String> maskKeys) {
        if (key == null || maskKeys == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String k : maskKeys) {
            if (k != null && lower.equals(k.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
