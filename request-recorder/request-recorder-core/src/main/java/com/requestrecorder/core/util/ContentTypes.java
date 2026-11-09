package com.requestrecorder.core.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content-Type 判定与字符集解析。
 */
public final class ContentTypes {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset\\s*=\\s*\"?([^\";,]+)\"?");

    private ContentTypes() {
    }

    /** 是否为可采集正文的文本类内容类型。 */
    public static boolean isTextual(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("text/")) {
            return true;
        }
        return lower.contains("json")
                || lower.contains("xml")
                || lower.contains("javascript")
                || lower.contains("x-www-form-urlencoded")
                || lower.contains("graphql")
                || lower.contains("yaml")
                || lower.contains("csv")
                || lower.contains("html");
    }

    /** 从 Content-Type 解析字符集，解析不到时使用默认值。 */
    public static Charset resolveCharset(String contentType, String declaredEncoding, Charset fallback) {
        if (declaredEncoding != null && !declaredEncoding.trim().isEmpty()) {
            Charset cs = safeCharset(declaredEncoding);
            if (cs != null) {
                return cs;
            }
        }
        if (contentType != null) {
            Matcher m = CHARSET_PATTERN.matcher(contentType);
            if (m.find()) {
                Charset cs = safeCharset(m.group(1));
                if (cs != null) {
                    return cs;
                }
            }
        }
        return fallback == null ? StandardCharsets.UTF_8 : fallback;
    }

    private static Charset safeCharset(String name) {
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否二进制（非文本）内容。 */
    public static boolean isBinary(String contentType) {
        return !isTextual(contentType);
    }
}
