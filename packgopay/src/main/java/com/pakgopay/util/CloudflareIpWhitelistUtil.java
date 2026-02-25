package com.pakgopay.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare IP whitelist helper.
 *
 * Usage:
 * cloudflareIpWhitelistUtil.addIps(ips, "merchant-add");
 */
@Service
public class CloudflareIpWhitelistUtil {
    private static final Logger log = LoggerFactory.getLogger(CloudflareIpWhitelistUtil.class);
    private static final String API_BASE = "https://api.cloudflare.com/client/v4";

    private final RestTemplate restTemplate;
    @Value("${pakgopay.cloudflare.apiToken:}")
    private String apiToken;

    @Value("${pakgopay.cloudflare.zoneId:}")
    private String zoneId;


    public CloudflareIpWhitelistUtil(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     *
     * @param ips whiteIps
     * @param comment some notes for operate
     * @return
     */
    public ResponseEntity<String> addIps(Collection<String> ips, String comment) {
        if (!StringUtils.hasText(apiToken)) {
            throw new IllegalArgumentException("pakgopay.cloudflare.apiToken is required");
        }
        if (!StringUtils.hasText(zoneId)) {
            throw new IllegalArgumentException("pakgopay.cloudflare.zoneId is required");
        }
        if (ips == null || ips.isEmpty()) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken.trim());

        ResponseEntity<String> lastResponse = null;
        for (String ip : ips) {
            if (!StringUtils.hasText(ip)) continue;
            String[] parts = ip.split(",");
            for (String part : parts) {
                if (!StringUtils.hasText(part)) continue;
                String ipValue = part.trim();
                if (ipValue.isEmpty()) continue;
            Map<String, Object> body = new HashMap<>();
            body.put("mode", "whitelist");
            Map<String, String> config = new HashMap<>();
            config.put("target", "ip");
            config.put("value", ipValue);
            body.put("configuration", config);
            if (StringUtils.hasText(comment)) {
                body.put("notes", comment);
            }

            String url = API_BASE + "/zones/" + zoneId + "/firewall/access_rules/rules";
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            try {
                lastResponse = restTemplate.postForEntity(url, entity, String.class);
            } catch (HttpStatusCodeException e) {
                String errorBody = e.getResponseBodyAsString();
                if (errorBody != null && errorBody.contains("\"code\":10009")) {
                    log.info("cloudflare whitelist skipped (duplicate): ip={}, zoneId={}", ipValue, zoneId);
                    continue;
                }
                String tokenHint = buildTokenHint(apiToken);
                log.warn("cloudflare whitelist failed: status={}, zoneId={}, token={}, body={}",
                        e.getStatusCode(), zoneId, tokenHint, errorBody);
                throw e;
            }
            }
        }
        return lastResponse;
    }

    private String buildTokenHint(String token) {
        if (!StringUtils.hasText(token)) {
            return "empty";
        }
        String trimmed = token.trim();
        int len = trimmed.length();
        String tail = len <= 4 ? trimmed : trimmed.substring(len - 4);
        return "len=" + len + ",tail=" + tail;
    }

}
