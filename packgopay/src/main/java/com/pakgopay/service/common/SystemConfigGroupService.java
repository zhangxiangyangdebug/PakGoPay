package com.pakgopay.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
import com.pakgopay.data.reqeust.systemConfig.SystemConfigGroupItemRequest;
import com.pakgopay.data.reqeust.systemConfig.SystemConfigGroupUpdateRequest;
import com.pakgopay.mapper.BusinessConfigurationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class SystemConfigGroupService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String GROUP_TELEGRAM = "telegram";
    private static final String GROUP_RATE_LIMIT = "ratelimit";

    private final TelegramService telegramService;
    private final RateLimitConfigService rateLimitConfigService;
    private final BusinessConfigurationMapper businessConfigurationMapper;

    public SystemConfigGroupService(
            TelegramService telegramService,
            RateLimitConfigService rateLimitConfigService,
            BusinessConfigurationMapper businessConfigurationMapper) {
        this.telegramService = telegramService;
        this.rateLimitConfigService = rateLimitConfigService;
        this.businessConfigurationMapper = businessConfigurationMapper;
    }

    public Map<String, Object> queryByGroup(String group) {
        String normalizedGroup = normalizeGroup(group);
        if (GROUP_TELEGRAM.equals(normalizedGroup)) {
            return queryTelegramConfig();
        }
        if (GROUP_RATE_LIMIT.equals(normalizedGroup)) {
            return queryRateLimitConfig();
        }
        throw new IllegalArgumentException("unsupported group: " + group);
    }

    public Map<String, Object> updateByGroup(SystemConfigGroupUpdateRequest request) {
        String normalizedGroup = normalizeGroup(request == null ? null : request.getGroup());
        Map<String, Object> configMap = collectAndValidateItems(normalizedGroup, request);
        if (GROUP_TELEGRAM.equals(normalizedGroup)) {
            applyTelegramConfig(configMap);
            return configMap;
        }
        if (GROUP_RATE_LIMIT.equals(normalizedGroup)) {
            applyRateLimitConfig(configMap);
            return configMap;
        }
        throw new IllegalArgumentException("unsupported group: " + normalizedGroup);
    }

    private Map<String, Object> collectAndValidateItems(
            String normalizedGroup, SystemConfigGroupUpdateRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (request == null || request.getConfigItems() == null) {
            return result;
        }
        for (SystemConfigGroupItemRequest item : request.getConfigItems()) {
            if (item == null || item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            if (!SystemConfigItemKeyEnum.isSupported(normalizedGroup, item.getKey())) {
                throw new IllegalArgumentException(
                        "unsupported config key for group " + normalizedGroup + ": " + item.getKey());
            }
            result.put(item.getKey(), item.getValue());
        }
        return result;
    }

    private void applyTelegramConfig(Map<String, Object> configMap) {
        telegramService.updateTelegramConfig(
                convertValue(configMap.get(SystemConfigItemKeyEnum.TELEGRAM_TOKEN.getKey()), String.class),
                convertValue(configMap.get(SystemConfigItemKeyEnum.TELEGRAM_CHAT_ID.getKey()), String.class),
                convertValue(configMap.get(SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET.getKey()), String.class),
                convertValue(configMap.get(SystemConfigItemKeyEnum.TELEGRAM_ALLOWED_USER_IDS.getKey()), String.class),
                convertValue(configMap.get(SystemConfigItemKeyEnum.TELEGRAM_ENABLED.getKey()), Integer.class));
    }

    private void applyRateLimitConfig(Map<String, Object> configMap) {
        Boolean enabled = convertBoolean(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_ENABLED.getKey()));
        Long windowSeconds = convertValue(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_WINDOW_SECONDS.getKey()), Long.class);
        Long maxRequests = convertValue(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_MAX_REQUESTS.getKey()), Long.class);
        String fixedIpQps = convertValue(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_FIXED_IP_QPS.getKey()), String.class);
        if (Boolean.TRUE.equals(enabled)
                && (windowSeconds == null || windowSeconds <= 0 || maxRequests == null || maxRequests <= 0)) {
            throw new IllegalArgumentException("windowSeconds/maxRequests must be greater than 0");
        }
        rateLimitConfigService.updateConfig(enabled, windowSeconds, maxRequests, fixedIpQps);
    }

    private Map<String, Object> queryTelegramConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put(SystemConfigItemKeyEnum.TELEGRAM_TOKEN.getKey(),
                getConfigValue(SystemConfigItemKeyEnum.TELEGRAM_TOKEN.buildDbKey()));
        result.put(SystemConfigItemKeyEnum.TELEGRAM_CHAT_ID.getKey(),
                getConfigValue(SystemConfigItemKeyEnum.TELEGRAM_CHAT_ID.buildDbKey()));
        result.put(SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET.getKey(),
                getConfigValue(SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET.buildDbKey()));
        result.put(SystemConfigItemKeyEnum.TELEGRAM_ALLOWED_USER_IDS.getKey(),
                getConfigValue(SystemConfigItemKeyEnum.TELEGRAM_ALLOWED_USER_IDS.buildDbKey()));
        result.put(SystemConfigItemKeyEnum.TELEGRAM_ENABLED.getKey(),
                getConfigValue(SystemConfigItemKeyEnum.TELEGRAM_ENABLED.buildDbKey()));
        return result;
    }

    private Map<String, Object> queryRateLimitConfig() {
        RateLimitConfigService.RateLimitConfig config = rateLimitConfigService.getConfig();
        Map<String, Object> result = new HashMap<>();
        result.put(SystemConfigItemKeyEnum.RATELIMIT_ENABLED.getKey(), config.isEnabled());
        result.put(SystemConfigItemKeyEnum.RATELIMIT_WINDOW_SECONDS.getKey(), config.getWindowSeconds());
        result.put(SystemConfigItemKeyEnum.RATELIMIT_MAX_REQUESTS.getKey(), config.getMaxRequests());
        result.put(SystemConfigItemKeyEnum.RATELIMIT_FIXED_IP_QPS.getKey(), config.getFixedIpQpsRaw());
        return result;
    }

    private String getConfigValue(String key) {
        try {
            String value = businessConfigurationMapper.getConfigValue(key);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("query config failed, key={}, message={}", key, e.getMessage());
            return null;
        }
    }

    private String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("group is empty");
        }
        return group.trim().toLowerCase(Locale.ROOT);
    }

    private Boolean convertBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text)) {
            return true;
        }
        if ("0".equals(text)) {
            return false;
        }
        return Boolean.parseBoolean(text);
    }

    private <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.convertValue(value, targetType);
        } catch (Exception e) {
            log.warn("convert config value failed, targetType={}, message={}",
                    targetType == null ? null : targetType.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
