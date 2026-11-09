package com.requestrecorder.core.serializer;

import com.requestrecorder.core.config.RecorderSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏测试：敏感头、JSON 正文、表单正文。
 */
class SensitiveMaskerTest {

    private RecorderSettings settings() {
        RecorderSettings settings = new RecorderSettings();
        settings.setMaskingEnabled(true);
        settings.setMaskHeaders(Arrays.asList("Authorization", "Cookie", "Set-Cookie"));
        settings.setMaskKeys(Arrays.asList("password", "token", "idCard"));
        return settings;
    }

    @Test
    @DisplayName("敏感请求头被脱敏，普通头保留")
    void sensitiveHeadersAreMasked() {
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        headers.put("Authorization", Arrays.asList("Bearer abcdef"));
        headers.put("User-Agent", Arrays.asList("junit"));
        String json = SensitiveMasker.headersToJson(headers, settings());
        assertTrue(json.contains(SensitiveMasker.MASK), json);
        assertFalse(json.contains("Bearer abcdef"), json);
        assertTrue(json.contains("junit"), json);
    }

    @Test
    @DisplayName("JSON 正文中敏感字段被递归脱敏")
    void jsonBodyKeysAreMasked() {
        String body = "{\"username\":\"zhangsan\",\"password\":\"123456\",\"nested\":{\"token\":\"t-1\"}}";
        String masked = SensitiveMasker.maskBody(body, "application/json", settings());
        assertFalse(masked.contains("123456"), masked);
        assertFalse(masked.contains("t-1"), masked);
        assertTrue(masked.contains("zhangsan"), masked);
    }

    @Test
    @DisplayName("表单正文中敏感字段被脱敏")
    void formBodyKeysAreMasked() {
        String masked = SensitiveMasker.maskBody("username=zhangsan&password=123456", 
                "application/x-www-form-urlencoded", settings());
        assertFalse(masked.contains("123456"), masked);
        assertTrue(masked.contains("zhangsan"), masked);
    }

    @Test
    @DisplayName("关闭脱敏后原样返回")
    void maskingCanBeDisabled() {
        RecorderSettings settings = settings();
        settings.setMaskingEnabled(false);
        String body = "{\"password\":\"123456\"}";
        assertTrue(body.equals(SensitiveMasker.maskBody(body, "application/json", settings)));
    }
}
