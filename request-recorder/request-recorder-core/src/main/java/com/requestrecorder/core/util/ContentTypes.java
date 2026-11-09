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

    /**
     * 响应字符集解析。容器在业务未显式声明编码时，{@code response.getCharacterEncoding()} 会返回
     * Servlet 规范的默认值 ISO-8859-1，不能当作真实声明，否则中文响应体整片乱码。
     * 优先级：Content-Type 中的 charset → 可信的显式声明 → 文本类回退 UTF-8。
     */
    public static Charset resolveResponseCharset(String contentType, String declaredEncoding, Charset fallback) {
        if (contentType != null) {
            Matcher m = CHARSET_PATTERN.matcher(contentType);
            if (m.find()) {
                Charset cs = safeCharset(m.group(1));
                if (cs != null) {
                    return cs;
                }
            }
        }
        if (declaredEncoding != null && !declaredEncoding.trim().isEmpty()) {
            Charset cs = safeCharset(declaredEncoding);
            if (cs != null && !isContainerDefault(cs)) {
                return cs;
            }
        }
        if (fallback != null && !isContainerDefault(fallback)) {
            return fallback;
        }
        return StandardCharsets.UTF_8;
    }

    /** 是否为容器/规范层面的兜底编码（ISO-8859-1、US-ASCII），不代表业务真实声明。 */
    private static boolean isContainerDefault(Charset charset) {
        return StandardCharsets.ISO_8859_1.equals(charset) || StandardCharsets.US_ASCII.equals(charset);
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
