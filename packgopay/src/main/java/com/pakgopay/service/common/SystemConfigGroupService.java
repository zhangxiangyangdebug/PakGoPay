package com.pakgopay.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.SystemConfigGroupEnum;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
import com.pakgopay.data.reqeust.systemConfig.SystemConfigGroupItemRequest;
import com.pakgopay.data.reqeust.systemConfig.SystemConfigGroupUpdateRequest;
import com.pakgopay.mapper.BusinessConfigurationMapper;
import com.pakgopay.thirdUtil.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
public class SystemConfigGroupService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CACHE_KEY_PREFIX = "system:config:group:";

    private final BusinessConfigurationMapper businessConfigurationMapper;
    private final RedisUtil redisUtil;

    @Value("${pakgopay.system-config.cache-seconds:300}")
    private int systemConfigCacheSeconds;

    public SystemConfigGroupService(
            BusinessConfigurationMapper businessConfigurationMapper,
            RedisUtil redisUtil) {
        this.businessConfigurationMapper = businessConfigurationMapper;
        this.redisUtil = redisUtil;
    }

    public Map<String, Object> queryByGroup(String group) {
        SystemConfigGroupEnum groupEnum = SystemConfigGroupEnum.fromGroup(group);
        return queryByGroupWithCache(
                groupEnum.getGroup(),
                () -> queryGroupConfig(resolveGroupKeys(groupEnum)));
    }

    public Map<String, Object> updateByGroup(SystemConfigGroupUpdateRequest request) {
        SystemConfigGroupEnum groupEnum = SystemConfigGroupEnum.fromGroup(request == null ? null : request.getGroup());
        Map<String, Object> configMap = collectAndValidateItems(groupEnum.getGroup(), request);
        if (SystemConfigGroupEnum.RATELIMIT == groupEnum) {
            validateRateLimitConfig(configMap);
        } else if (SystemConfigGroupEnum.COLLECTION == groupEnum) {
            validateOrderConfig(configMap,
                    SystemConfigItemKeyEnum.COLLECTION_ORDER_TIMEOUT_SECONDS,
                    SystemConfigItemKeyEnum.COLLECTION_CALLBACK_RETRY_TIMES,
                    SystemConfigItemKeyEnum.COLLECTION_CHANNEL_MATCH_MODE);
        } else if (SystemConfigGroupEnum.PAYOUT == groupEnum) {
            validateOrderConfig(configMap,
                    SystemConfigItemKeyEnum.PAYOUT_ORDER_TIMEOUT_SECONDS,
                    SystemConfigItemKeyEnum.PAYOUT_CALLBACK_RETRY_TIMES,
                    SystemConfigItemKeyEnum.PAYOUT_CHANNEL_MATCH_MODE);
        }
        upsertGroupConfig(configMap, resolveGroupKeys(groupEnum));
        refreshGroupCache(groupEnum.getGroup(), () -> queryGroupConfig(resolveGroupKeys(groupEnum)));
        return configMap;
    }

    /**
     * Internal access: read config value by enum key.
     */
    public Object getConfigValue(SystemConfigItemKeyEnum itemKey) {
        return getConfigValue(itemKey, Object.class);
    }

    /**
     * Internal access: read config value by enum key and target type.
     */
    public <T> T getConfigValue(SystemConfigItemKeyEnum itemKey, Class<T> targetType) {
        if (itemKey == null) {
            throw new IllegalArgumentException("itemKey is null");
        }
        Map<String, Object> groupConfig = queryByGroup(itemKey.getGroup());
        Object value = groupConfig == null ? null : groupConfig.get(itemKey.getKey());
        return convertValue(value, targetType);
    }

    /**
     * Internal access: read config value by enum key with fallback default value.
     */
    public <T> T getConfigValue(SystemConfigItemKeyEnum itemKey, Class<T> targetType, T defaultValue) {
        T value = getConfigValue(itemKey, targetType);
        return value == null ? defaultValue : value;
    }

    /**
     * Internal access: update one config item and refresh group cache immediately.
     */
    public void setConfigValue(SystemConfigItemKeyEnum itemKey, Object value) {
        if (itemKey == null || value == null) {
            return;
        }
        upsertIfPresent(itemKey, value);
        String group = itemKey.getGroup();
        refreshGroupCache(group, () -> queryGroupConfig(resolveGroupKeys(SystemConfigGroupEnum.fromGroup(group))));
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

    private void validateRateLimitConfig(Map<String, Object> configMap) {
        Boolean enabled = convertBoolean(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_ENABLED.getKey()));
        Long windowSeconds = convertValue(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_WINDOW_SECONDS.getKey()), Long.class);
        Long maxRequests = convertValue(configMap.get(SystemConfigItemKeyEnum.RATELIMIT_MAX_REQUESTS.getKey()), Long.class);
        if (Boolean.TRUE.equals(enabled)
                && (windowSeconds == null || windowSeconds <= 0 || maxRequests == null || maxRequests <= 0)) {
            throw new IllegalArgumentException("windowSeconds/maxRequests must be greater than 0");
        }
    }

    private void validateOrderConfig(Map<String, Object> configMap,
                                     SystemConfigItemKeyEnum timeoutKey,
                                     SystemConfigItemKeyEnum retryKey,
                                     SystemConfigItemKeyEnum modeKey) {
        Integer orderTimeoutSeconds = convertValue(configMap.get(timeoutKey.getKey()), Integer.class);
        Integer callbackRetryTimes = convertValue(configMap.get(retryKey.getKey()), Integer.class);
        Integer channelMatchMode = convertValue(configMap.get(modeKey.getKey()), Integer.class);

        if (orderTimeoutSeconds != null && orderTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("orderTimeoutSeconds must be greater than 0");
        }
        if (callbackRetryTimes != null && callbackRetryTimes <= 0) {
            throw new IllegalArgumentException("callbackRetryTimes must be greater than 0");
        }
        if (channelMatchMode != null && channelMatchMode <= 0) {
            throw new IllegalArgumentException("channelMatchMode must be greater than 0");
        }
    }

    private Map<String, Object> queryGroupConfig(SystemConfigItemKeyEnum... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (keys == null) {
            return result;
        }
        for (SystemConfigItemKeyEnum key : keys) {
            if (key == null) {
                continue;
            }
            result.put(key.getKey(), getConfigValueFromDb(key.buildDbKey()));
        }
        return result;
    }

    private void upsertGroupConfig(Map<String, Object> configMap, SystemConfigItemKeyEnum... keys) {
        if (configMap == null || keys == null) {
            return;
        }
        for (SystemConfigItemKeyEnum key : keys) {
            if (key == null) {
                continue;
            }
            upsertIfPresent(key, configMap.get(key.getKey()));
        }
    }

    private SystemConfigItemKeyEnum[] resolveGroupKeys(SystemConfigGroupEnum groupEnum) {
        if (SystemConfigGroupEnum.TELEGRAM == groupEnum) {
            return new SystemConfigItemKeyEnum[]{
                    SystemConfigItemKeyEnum.TELEGRAM_TOKEN,
                    SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET,
                    SystemConfigItemKeyEnum.TELEGRAM_ENABLED,
                    SystemConfigItemKeyEnum.TELEGRAM_CONSOLE_URL
            };
        }
        if (SystemConfigGroupEnum.RATELIMIT == groupEnum) {
            return new SystemConfigItemKeyEnum[]{
                    SystemConfigItemKeyEnum.RATELIMIT_ENABLED,
                    SystemConfigItemKeyEnum.RATELIMIT_WINDOW_SECONDS,
                    SystemConfigItemKeyEnum.RATELIMIT_MAX_REQUESTS,
                    SystemConfigItemKeyEnum.RATELIMIT_FIXED_IP_QPS
            };
        }
        if (SystemConfigGroupEnum.COLLECTION == groupEnum) {
            return new SystemConfigItemKeyEnum[]{
                    SystemConfigItemKeyEnum.COLLECTION_ORDER_TIMEOUT_SECONDS,
                    SystemConfigItemKeyEnum.COLLECTION_CALLBACK_RETRY_TIMES,
                    SystemConfigItemKeyEnum.COLLECTION_CHANNEL_MATCH_MODE
            };
        }
        if (SystemConfigGroupEnum.PAYOUT == groupEnum) {
            return new SystemConfigItemKeyEnum[]{
                    SystemConfigItemKeyEnum.PAYOUT_ORDER_TIMEOUT_SECONDS,
                    SystemConfigItemKeyEnum.PAYOUT_CALLBACK_RETRY_TIMES,
                    SystemConfigItemKeyEnum.PAYOUT_CHANNEL_MATCH_MODE
            };
        }
        return new SystemConfigItemKeyEnum[0];
    }

    private String getConfigValueFromDb(String key) {
        try {
            String value = businessConfigurationMapper.getConfigValue(key);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("query config failed, key={}, message={}", key, e.getMessage());
            return null;
        }
    }

    private void upsertIfPresent(SystemConfigItemKeyEnum itemKey, Object value) {
        if (itemKey == null || value == null) {
            return;
        }
        String dbKey = itemKey.buildDbKey();
        String dbValue = String.valueOf(value);
        int updated = businessConfigurationMapper.updateConfigValue(dbKey, dbValue);
        if (updated <= 0) {
            businessConfigurationMapper.insertConfig(dbKey, dbValue);
        }
    }

    private Map<String, Object> queryByGroupWithCache(String group, Supplier<Map<String, Object>> dbLoader) {
        String cacheKey = buildGroupCacheKey(group);
        String cached = redisUtil.getValue(cacheKey);
        if (cached != null && !cached.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(cached, Map.class);
            } catch (Exception e) {
                log.warn("system config cache parse failed, group={}, message={}", group, e.getMessage());
                redisUtil.remove(cacheKey);
            }
        }
        Map<String, Object> loaded = dbLoader.get();
        writeGroupCache(group, loaded);
        return loaded;
    }

    private void refreshGroupCache(String group, Supplier<Map<String, Object>> dbLoader) {
        writeGroupCache(group, dbLoader.get());
    }

    private void writeGroupCache(String group, Map<String, Object> data) {
        String cacheKey = buildGroupCacheKey(group);
        try {
            redisUtil.remove(cacheKey);
            redisUtil.setWithSecondExpire(
                    cacheKey,
                    OBJECT_MAPPER.writeValueAsString(data == null ? Map.of() : data),
                    systemConfigCacheSeconds);
        } catch (Exception e) {
            log.warn("system config cache write failed, group={}, message={}", group, e.getMessage());
        }
    }

    private String buildGroupCacheKey(String group) {
        return CACHE_KEY_PREFIX + group;
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
