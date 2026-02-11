package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pakgopay.mapper.BusinessConfigurationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TelegramService {

    private static final String TOKEN_KEY = "telegram.token";
    private static final String CHAT_ID_KEY = "telegram.chatId";
    private static final String WEBHOOK_SECRET_KEY = "telegram.webhookSecret";
    private static final String API_BASE = "https://api.telegram.org";

    private final RestTemplate restTemplate;
    private final BusinessConfigurationMapper configMapper;

    public TelegramService(RestTemplate restTemplate, BusinessConfigurationMapper configMapper) {
        this.restTemplate = restTemplate;
        this.configMapper = configMapper;
    }

    public String sendMessage(String text) {
        String chatId = getConfig(CHAT_ID_KEY);
        return sendMessageTo(chatId, text);
    }

    public String sendMessageTo(String chatId, String text) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(chatId)) {
            log.warn("Telegram chatId not configured.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/sendMessage";
        Map<String, String> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        String result = restTemplate.postForObject(url, body, String.class);
        if (result == null || !result.contains("\"ok\":true")) {
            log.warn("Telegram sendMessage failed: {}", result);
        }
        return result;
    }

    public String getUpdates() {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/getUpdates";
        return restTemplate.getForObject(url, String.class);
    }

    public String getWebhookSecret() {
        return getConfig(WEBHOOK_SECRET_KEY);
    }

    public Map<String, String> getTelegramConfig() {
        Map<String, String> result = new HashMap<>();
        result.put("token", getConfig(TOKEN_KEY));
        result.put("chatId", getConfig(CHAT_ID_KEY));
        result.put("webhookSecret", getConfig(WEBHOOK_SECRET_KEY));
        return result;
    }

    public void updateTelegramConfig(String token, String chatId, String webhookSecret) {
        upsertConfig(TOKEN_KEY, token);
        upsertConfig(CHAT_ID_KEY, chatId);
        upsertConfig(WEBHOOK_SECRET_KEY, webhookSecret);
    }

    private String getConfig(String key) {
        try {
            String value = configMapper.getConfigValue(key);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("Get config {} failed: {}", key, e.getMessage());
            return null;
        }
    }

    private void upsertConfig(String key, String value) {
        try {
            int updated = configMapper.updateConfigValue(key, value);
            if (updated == 0) {
                configMapper.insertConfig(key, value);
            }
        } catch (Exception e) {
            log.warn("Update config {} failed: {}", key, e.getMessage());
        }
    }
}
