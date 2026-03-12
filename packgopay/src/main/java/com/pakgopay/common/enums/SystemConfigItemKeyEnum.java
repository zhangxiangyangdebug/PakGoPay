package com.pakgopay.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;

@Getter
public enum SystemConfigItemKeyEnum {
    TELEGRAM_TOKEN("telegram", "token"),
    TELEGRAM_CHAT_ID("telegram", "chatId"),
    TELEGRAM_WEBHOOK_SECRET("telegram", "webhookSecret"),
    TELEGRAM_ALLOWED_USER_IDS("telegram", "allowedUserIds"),
    TELEGRAM_ENABLED("telegram", "enabled"),

    RATELIMIT_ENABLED("ratelimit", "enabled"),
    RATELIMIT_WINDOW_SECONDS("ratelimit", "windowSeconds"),
    RATELIMIT_MAX_REQUESTS("ratelimit", "maxRequests"),
    RATELIMIT_FIXED_IP_QPS("ratelimit", "fixedIpQps");

    private final String group;
    private final String key;

    SystemConfigItemKeyEnum(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public static boolean isSupported(String group, String key) {
        if (group == null || key == null) {
            return false;
        }
        String normalizedGroup = group.trim().toLowerCase(Locale.ROOT);
        String normalizedKey = key.trim();
        return Arrays.stream(values()).anyMatch(item ->
                item.group.equals(normalizedGroup) && item.key.equals(normalizedKey));
    }

    public String buildDbKey() {
        return group + "." + key;
    }
}
