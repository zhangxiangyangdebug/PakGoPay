package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pakgopay.mapper.BusinessConfigurationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class TelegramService {

    private static final String TOKEN_KEY = "telegram.token";
    private static final String CHAT_ID_KEY = "telegram.chatId";
    private static final String WEBHOOK_SECRET_KEY = "telegram.webhookSecret";
    private static final String ALLOWED_USER_IDS_KEY = "telegram.allowedUserIds";
    private static final String ENABLED_KEY = "telegram.enabled";
    private static final String CONSOLE_URL_KEY = "telegram.consoleUrl";
    private static final String API_BASE = "https://api.telegram.org";

    private final RestTemplate restTemplate;
    private final RestTemplate telegramRestTemplate = new RestTemplate();
    private final BusinessConfigurationMapper configMapper;
    private volatile String cachedBotUsername;
    private final AtomicLong cachedBotUsernameAtMs = new AtomicLong(0L);

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
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            String result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
                return result;
            }
            log.warn("Telegram sendMessage failed: status={}, body={}", response.getStatusCode(), result);
            return result;
        } catch (Exception e) {
            log.warn("Telegram sendMessage failed: {}", e.getMessage());
            throw e;
        }
    }

    public String sendMessageWithInlineKeyboardTo(String chatId, String text, Object replyMarkup) {
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
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        try {
            ResponseEntity<String> response = telegramRestTemplate.postForEntity(url, body, String.class);
            String result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
                return result;
            }
            log.warn("Telegram sendMessageWithInlineKeyboard failed: status={}, body={}", response.getStatusCode(), result);
            return result;
        } catch (HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.warn("Telegram sendMessageWithInlineKeyboard failed: status={}, body={}",
                    e.getStatusCode(), errorBody);
            String migratedChatId = extractMigratedChatId(errorBody);
            if (StringUtils.hasText(migratedChatId)) {
                log.info("Telegram chat migrated, oldChatId={}, newChatId={}", chatId, migratedChatId);
                // keep config in sync when default chat has migrated to supergroup
                String defaultChatId = getDefaultChatId();
                if (StringUtils.hasText(defaultChatId) && defaultChatId.equals(chatId)) {
                    upsertConfig(CHAT_ID_KEY, migratedChatId);
                }
                try {
                    body.put("chat_id", migratedChatId);
                    ResponseEntity<String> retryResponse = telegramRestTemplate.postForEntity(url, body, String.class);
                    String retryBody = retryResponse.getBody();
                    if (retryResponse.getStatusCode().is2xxSuccessful()
                            && retryBody != null && retryBody.contains("\"ok\":true")) {
                        log.info("Telegram resend after migration success, chatId={}", migratedChatId);
                        return retryBody;
                    }
                    log.warn("Telegram resend after migration failed: status={}, body={}",
                            retryResponse.getStatusCode(), retryBody);
                } catch (Exception retryEx) {
                    log.warn("Telegram resend after migration exception: {}", retryEx.getMessage());
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Telegram sendMessageWithInlineKeyboard failed: {}", e.getMessage());
            return null;
        }
    }

    public String editMessageText(String chatId, Long messageId, String text, Object replyMarkup) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(chatId) || messageId == null) {
            log.warn("Telegram editMessageText skipped: invalid chatId or messageId.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/editMessageText";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            String result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
                return result;
            }
            log.warn("Telegram editMessageText failed: status={}, body={}", response.getStatusCode(), result);
            return result;
        } catch (Exception e) {
            log.warn("Telegram editMessageText failed: {}", e.getMessage());
            throw e;
        }
    }

    public String answerCallbackQuery(String callbackQueryId, String text, Boolean showAlert) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(callbackQueryId)) {
            log.warn("Telegram answerCallbackQuery skipped: callbackQueryId is empty.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/answerCallbackQuery";
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (StringUtils.hasText(text)) {
            body.put("text", text);
        }
        if (showAlert != null) {
            body.put("show_alert", showAlert);
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            String result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
                return result;
            }
            log.warn("Telegram answerCallbackQuery failed: status={}, body={}", response.getStatusCode(), result);
            return result;
        } catch (Exception e) {
            log.warn("Telegram answerCallbackQuery failed: {}", e.getMessage());
            throw e;
        }
    }

    public String sendDocumentTo(String chatId, String fileName, byte[] content) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(chatId)) {
            log.warn("Telegram chatId not configured.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/sendDocument";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("document", resource);

        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        String result = response.getBody();
        if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
            return result;
        }
        log.warn("Telegram sendDocument failed: status={}, body={}", response.getStatusCode(), result);
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

    public byte[] downloadFileBytes(String fileId) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(fileId)) {
            log.warn("Telegram downloadFile skipped: fileId is empty.");
            return null;
        }
        try {
            String getFileUrl = API_BASE + "/bot" + token + "/getFile?file_id=" + fileId;
            String getFileResult = restTemplate.getForObject(getFileUrl, String.class);
            if (!StringUtils.hasText(getFileResult)) {
                log.warn("Telegram getFile failed: empty response, fileId={}", fileId);
                return null;
            }
            JSONObject root = JSON.parseObject(getFileResult);
            if (root == null || !Boolean.TRUE.equals(root.getBoolean("ok"))) {
                log.warn("Telegram getFile failed: {}", getFileResult);
                return null;
            }
            JSONObject result = root.getJSONObject("result");
            String filePath = result == null ? null : result.getString("file_path");
            if (!StringUtils.hasText(filePath)) {
                log.warn("Telegram getFile failed: file_path empty, fileId={}", fileId);
                return null;
            }
            String fileUrl = API_BASE + "/file/bot" + token + "/" + filePath;
            ResponseEntity<byte[]> fileResp = restTemplate.getForEntity(fileUrl, byte[].class);
            if (!fileResp.getStatusCode().is2xxSuccessful()) {
                log.warn("Telegram download file failed: status={}, fileId={}", fileResp.getStatusCode(), fileId);
                return null;
            }
            return fileResp.getBody();
        } catch (Exception e) {
            log.warn("Telegram download file failed, fileId={}, message={}", fileId, e.getMessage());
            return null;
        }
    }

    public String getWebhookSecret() {
        return getConfig(WEBHOOK_SECRET_KEY);
    }

    public boolean isAllowedUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        String value = getConfig(ALLOWED_USER_IDS_KEY);
        if (!StringUtils.hasText(value)) {
            log.warn("Telegram allowed user ids not configured.");
            return false;
        }
        String normalized = value
                .replace('，', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replaceAll("\\s+", ",");
        String[] parts = normalized.split(",");
        for (String part : parts) {
            if (userId.trim().equals(part.trim())) {
                return true;
            }
        }
        log.warn("Telegram user not allowed, fromUserId={}, allowedUserIdsRaw={}, allowedUserIdsNormalized={}",
                userId, value, normalized);
        return false;
    }

    public String getDefaultChatId() {
        return getConfig(CHAT_ID_KEY);
    }

    public String getConsoleBaseUrl() {
        return getConfig(CONSOLE_URL_KEY);
    }

    public void updateTelegramConfig(String token, String chatId, String webhookSecret, String allowedUserIds, Integer enabled) {
        upsertConfig(TOKEN_KEY, token);
        upsertConfig(CHAT_ID_KEY, chatId);
        upsertConfig(WEBHOOK_SECRET_KEY, webhookSecret);
        upsertConfig(ALLOWED_USER_IDS_KEY, allowedUserIds);
        if (enabled != null) {
            upsertConfig(ENABLED_KEY, String.valueOf(enabled));
        }
    }

    public boolean isEnabled() {
        String value = getConfig(ENABLED_KEY);
        if (!StringUtils.hasText(value)) {
            return true;
        }
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    public String getBotUsername() {
        long now = System.currentTimeMillis();
        String cached = cachedBotUsername;
        if (StringUtils.hasText(cached) && now - cachedBotUsernameAtMs.get() < 10 * 60 * 1000L) {
            return cached;
        }
        synchronized (this) {
            cached = cachedBotUsername;
            if (StringUtils.hasText(cached) && now - cachedBotUsernameAtMs.get() < 10 * 60 * 1000L) {
                return cached;
            }
            String token = getConfig(TOKEN_KEY);
            if (!StringUtils.hasText(token)) {
                return null;
            }
            try {
                String url = API_BASE + "/bot" + token + "/getMe";
                String body = telegramRestTemplate.getForObject(url, String.class);
                if (!StringUtils.hasText(body)) {
                    return null;
                }
                JSONObject root = JSON.parseObject(body);
                if (root == null || !Boolean.TRUE.equals(root.getBoolean("ok"))) {
                    return null;
                }
                JSONObject result = root.getJSONObject("result");
                String username = result == null ? null : result.getString("username");
                if (!StringUtils.hasText(username)) {
                    return null;
                }
                cachedBotUsername = username.trim();
                cachedBotUsernameAtMs.set(now);
                return cachedBotUsername;
            } catch (Exception e) {
                log.warn("Telegram getMe failed: {}", e.getMessage());
                return null;
            }
        }
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

    private String extractMigratedChatId(String errorBody) {
        if (!StringUtils.hasText(errorBody)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(errorBody);
            if (root == null) {
                return null;
            }
            JSONObject parameters = root.getJSONObject("parameters");
            if (parameters == null) {
                return null;
            }
            Long migratedChatId = parameters.getLong("migrate_to_chat_id");
            return migratedChatId == null ? null : String.valueOf(migratedChatId);
        } catch (Exception e) {
            log.warn("Parse telegram migrate_to_chat_id failed: {}", e.getMessage());
            return null;
        }
    }
}
