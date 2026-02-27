package com.pakgopay.service.common;

import com.pakgopay.mapper.BusinessConfigurationMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class RateLimitConfigService {
    private static final String KEY_ENABLED = "scasconfig.ratelimit.enabled";
    private static final String KEY_WINDOW_SECONDS = "scasconfig.ratelimit.windowSeconds";
    private static final String KEY_MAX_REQUESTS = "scasconfig.ratelimit.maxRequests";
    private static final String KEY_FIXED_IP_QPS = "scasconfig.ratelimit.fixedIpQps";

    private final BusinessConfigurationMapper configMapper;

    @Value("${pakgopay.rate-limit.enabled:false}")
    private boolean defaultEnabled;

    @Value("${pakgopay.rate-limit.window-seconds:0}")
    private long defaultWindowSeconds;

    @Value("${pakgopay.rate-limit.max-requests:0}")
    private long defaultMaxRequests;

    @Value("${pakgopay.rate-limit.config-refresh-ms:3000}")
    private long refreshMs;

    private volatile CachedConfig cached;

    public RateLimitConfigService(BusinessConfigurationMapper configMapper) {
        this.configMapper = configMapper;
    }

    public RateLimitConfig getConfig() {
        long now = System.currentTimeMillis();
        CachedConfig snapshot = cached;
        if (snapshot != null && now - snapshot.loadedAt < refreshMs) {
            return snapshot.config;
        }
        synchronized (this) {
            snapshot = cached;
            if (snapshot != null && now - snapshot.loadedAt < refreshMs) {
                return snapshot.config;
            }
            RateLimitConfig config = loadConfig();
            cached = new CachedConfig(config, now);
            return config;
        }
    }

    public Map<String, Object> getConfigAsMap() {
        RateLimitConfig config = getConfig();
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", config.isEnabled());
        map.put("windowSeconds", config.getWindowSeconds());
        map.put("maxRequests", config.getMaxRequests());
        map.put("fixedIpQps", config.getFixedIpQpsRaw());
        return map;
    }

    public void updateConfig(Boolean enabled, Long windowSeconds, Long maxRequests, String fixedIpQps) {
        if (enabled != null) {
            upsert(KEY_ENABLED, enabled ? "1" : "0");
        }
        if (windowSeconds != null) {
            upsert(KEY_WINDOW_SECONDS, String.valueOf(windowSeconds));
        }
        if (maxRequests != null) {
            upsert(KEY_MAX_REQUESTS, String.valueOf(maxRequests));
        }
        if (fixedIpQps != null) {
            upsert(KEY_FIXED_IP_QPS, fixedIpQps);
        }
        cached = null;
    }

    private RateLimitConfig loadConfig() {
        boolean enabled = parseEnabled(getConfigValue(KEY_ENABLED), defaultEnabled);
        long windowSeconds = parseLong(getConfigValue(KEY_WINDOW_SECONDS), defaultWindowSeconds);
        long maxRequests = parseLong(getConfigValue(KEY_MAX_REQUESTS), defaultMaxRequests);
        String fixedIpQpsRaw = getConfigValue(KEY_FIXED_IP_QPS);
        return new RateLimitConfig(enabled, windowSeconds, maxRequests, fixedIpQpsRaw);
    }

    private String getConfigValue(String key) {
        try {
            String value = configMapper.getConfigValue(key);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("Get config {} failed: {}", key, e.getMessage());
            return null;
        }
    }

    private void upsert(String key, String value) {
        try {
            int updated = configMapper.updateConfigValue(key, value);
            if (updated == 0) {
                configMapper.insertConfig(key, value);
            }
        } catch (Exception e) {
            log.warn("Update config {} failed: {}", key, e.getMessage());
        }
    }

    private boolean parseEnabled(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    private long parseLong(String value, long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Getter
    public static class RateLimitConfig {
        private final boolean enabled;
        private final long windowSeconds;
        private final long maxRequests;
        private final String fixedIpQpsRaw;
        private final Map<String, Long> fixedIpQpsMap;

        public RateLimitConfig(boolean enabled, long windowSeconds, long maxRequests, String fixedIpQpsRaw) {
            this.enabled = enabled;
            this.windowSeconds = windowSeconds;
            this.maxRequests = maxRequests;
            this.fixedIpQpsRaw = fixedIpQpsRaw;
            this.fixedIpQpsMap = parseFixedIpQps(fixedIpQpsRaw);
        }

        private Map<String, Long> parseFixedIpQps(String raw) {
            if (!StringUtils.hasText(raw)) {
                return Map.of();
            }
            Map<String, Long> result = new HashMap<>();
            String normalized = raw.replace('，', ',');
            String[] entries = normalized.split("[,\\n;]");
            for (String entry : entries) {
                String item = entry.trim();
                if (item.isEmpty()) {
                    continue;
                }
                String[] parts = item.split("/");
                if (parts.length != 2) {
                    parts = item.split(":");
                }
                if (parts.length != 2) {
                    continue;
                }
                String ip = parts[0].trim();
                String qps = parts[1].trim();
                if (!StringUtils.hasText(ip) || !StringUtils.hasText(qps)) {
                    continue;
                }
                try {
                    long limit = Long.parseLong(qps);
                    if (limit > 0) {
                        result.put(ip, limit);
                    }
                } catch (NumberFormatException ignored) {
                    // skip invalid
                }
            }
            return result;
        }
    }

    private static class CachedConfig {
        private final RateLimitConfig config;
        private final long loadedAt;

        private CachedConfig(RateLimitConfig config, long loadedAt) {
            this.config = config;
            this.loadedAt = loadedAt;
        }
    }
}
