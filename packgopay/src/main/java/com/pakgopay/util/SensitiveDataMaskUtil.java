package com.pakgopay.util;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SensitiveDataMaskUtil {

    private SensitiveDataMaskUtil() {
    }

    public static String maskKeepHeadTail(String value, int head, int tail) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.trim();
        int len = text.length();
        int safeHead = Math.max(0, head);
        int safeTail = Math.max(0, tail);
        if (len <= safeHead + safeTail) {
            return "*".repeat(len);
        }
        return text.substring(0, safeHead)
                + "*".repeat(len - safeHead - safeTail)
                + text.substring(len - safeTail);
    }

    public static String maskKeepHeadTail2(String value) {
        return maskKeepHeadTail(value, 2, 2);
    }

    public static String maskValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }

    public static boolean isSensitiveKeyContains(String key, Set<String> keywords) {
        if (key == null || key.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lowerKey.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static Object sanitizePayload(Object payload, Set<String> sensitiveKeywords) {
        return sanitize(payload, sensitiveKeywords);
    }

    @SuppressWarnings("unchecked")
    private static Object sanitize(Object value, Set<String> sensitiveKeywords) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                Object current = entry.getValue();
                if (isSensitiveKeyContains(key, sensitiveKeywords)) {
                    result.put(key, maskValue(current));
                } else {
                    result.put(key, sanitize(current, sensitiveKeywords));
                }
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            Collection<Object> result = value instanceof Set<?> ? new java.util.LinkedHashSet<>() : new ArrayList<>();
            for (Object item : collection) {
                result.add(sanitize(item, sensitiveKeywords));
            }
            return result;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    Object nested = JSON.parse(trimmed);
                    return JSON.toJSONString(sanitize(nested, sensitiveKeywords));
                } catch (Exception ignored) {
                    return text;
                }
            }
            return text;
        }
        return value;
    }
}

