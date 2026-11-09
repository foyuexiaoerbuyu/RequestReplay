package com.requestrecorder.core.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 轻量 Ant 风格路径匹配器，支持 {@code *}（单层任意字符）、{@code **}（跨层任意）与 {@code ?}。
 * 不依赖 Spring，供 core 与各 starter 共用。
 */
public final class AntPathMatcher {

    private AntPathMatcher() {
    }

    public static boolean matchAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty() || path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.trim().isEmpty()) {
                continue;
            }
            if (match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean match(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        return Pattern.compile(toRegex(pattern)).matcher(path).matches();
    }

    /** Ant 表达式转正则。 */
    static String toRegex(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        int len = pattern.length();
        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < len && pattern.charAt(i + 1) == '*';
                if (doubleStar) {
                    i += 2;
                    // "/**" 结尾或 "**" 单独出现：跨层匹配
                    if (i < len && pattern.charAt(i) == '/') {
                        i++;
                        sb.append("(?:.*/)?");
                    } else {
                        sb.append(".*");
                    }
                } else {
                    i++;
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        sb.append('$');
        return sb.toString();
    }
}
