package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
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
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class TelegramService {

    private static final SystemConfigItemKeyEnum TOKEN_KEY = SystemConfigItemKeyEnum.TELEGRAM_TOKEN;
    private static final SystemConfigItemKeyEnum WEBHOOK_SECRET_KEY = SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET;
    private static final SystemConfigItemKeyEnum ENABLED_KEY = SystemConfigItemKeyEnum.TELEGRAM_ENABLED;
    private static final SystemConfigItemKeyEnum CONSOLE_URL_KEY = SystemConfigItemKeyEnum.TELEGRAM_CONSOLE_URL;
    private static final String API_BASE = "https://api.telegram.org";

    private final RestTemplate restTemplate;
    private final RestTemplate telegramRestTemplate = new RestTemplate();
    private final SystemConfigGroupService systemConfigGroupService;
    private volatile String cachedBotUsername;
    private final AtomicLong cachedBotUsernameAtMs = new AtomicLong(0L);

    public TelegramService(RestTemplate restTemplate, SystemConfigGroupService systemConfigGroupService) {
        this.restTemplate = restTemplate;
        this.systemConfigGroupService = systemConfigGroupService;
    }

    public String sendMessage(String text) {
        log.warn("Telegram default chatId config has been removed, sendMessage is ignored.");
        return null;
    }

    public String sendMessageTo(String chatId, String text) {
        return sendMessageTo(chatId, text, null);
    }

    public String sendMessageTo(String chatId, String text, String parseMode) {
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
        if (StringUtils.hasText(parseMode)) {
            body.put("parse_mode", parseMode);
        }
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

    public String sendAnnouncementTo(String chatId, String title, String content, boolean pinMessage) {
        String formattedText = buildAnnouncementText(title, content);
        String result = sendMessageTo(chatId, formattedText, "HTML");
        if (pinMessage) {
            Long messageId = extractMessageId(result);
            if (messageId != null) {
                pinChatMessage(chatId, messageId, true);
            }
        }
        return result;
    }

    public String sendBroadcastContentTo(String chatId,
                                         String title,
                                         String content,
                                         String imageName,
                                         String imageDataUrl,
                                         boolean pinMessage) {
        boolean hasImage = StringUtils.hasText(imageDataUrl);
        boolean hasText = StringUtils.hasText(title) || StringUtils.hasText(content);
        if (!hasImage) {
            return sendAnnouncementTo(chatId, title, content, pinMessage);
        }

        DecodedTelegramImage image = decodeImageDataUrl(imageName, imageDataUrl);
        if (image == null) {
            throw new IllegalArgumentException("invalid image data");
        }

        String caption = hasText ? buildAnnouncementText(title, content) : null;
        if (StringUtils.hasText(caption) && caption.length() <= 1024) {
            return sendPhotoTo(chatId, image.fileName(), image.bytes(), caption, "HTML", pinMessage);
        }

        String photoResult = sendPhotoTo(chatId, image.fileName(), image.bytes(), null, null, !hasText && pinMessage);
        if (hasText) {
            sendAnnouncementTo(chatId, title, content, pinMessage);
        }
        return photoResult;
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

    public String pinChatMessage(String chatId, Long messageId, boolean disableNotification) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(chatId) || messageId == null) {
            log.warn("Telegram pinChatMessage skipped: invalid chatId or messageId.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/pinChatMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("disable_notification", disableNotification);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            String result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
                return result;
            }
            log.warn("Telegram pinChatMessage failed: status={}, body={}", response.getStatusCode(), result);
            return result;
        } catch (Exception e) {
            log.warn("Telegram pinChatMessage failed: {}", e.getMessage());
            return null;
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

    public String sendPhotoTo(String chatId,
                              String fileName,
                              byte[] content,
                              String caption,
                              String parseMode,
                              boolean pinMessage) {
        String token = getConfig(TOKEN_KEY);
        if (!StringUtils.hasText(token)) {
            log.warn("Telegram token not configured.");
            return null;
        }
        if (!StringUtils.hasText(chatId)) {
            log.warn("Telegram chatId not configured.");
            return null;
        }
        String url = API_BASE + "/bot" + token + "/sendPhoto";

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
        body.add("photo", resource);
        if (StringUtils.hasText(caption)) {
            body.add("caption", caption);
        }
        if (StringUtils.hasText(parseMode)) {
            body.add("parse_mode", parseMode);
        }

        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        String result = response.getBody();
        if (response.getStatusCode().is2xxSuccessful() && result != null && result.contains("\"ok\":true")) {
            if (pinMessage) {
                Long messageId = extractMessageId(result);
                if (messageId != null) {
                    pinChatMessage(chatId, messageId, true);
                }
            }
            return result;
        }
        log.warn("Telegram sendPhoto failed: status={}, body={}", response.getStatusCode(), result);
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

    public String getConsoleBaseUrl() {
        return getConfig(CONSOLE_URL_KEY);
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

    private String getConfig(SystemConfigItemKeyEnum key) {
        try {
            String value = systemConfigGroupService.getConfigValue(key, String.class);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("Get config {} failed: {}", key, e.getMessage());
            return null;
        }
    }

    private String buildAnnouncementText(String title, String content) {
        String safeTitle = HtmlUtils.htmlEscape(StringUtils.hasText(title) ? title.trim() : "系统公告");
        String safeContent = HtmlUtils.htmlEscape(content == null ? "" : content.trim())
                .replace("\r\n", "\n");
        return "<b>[" + safeTitle + "]</b>\n\n" + safeContent;
    }

    private Long extractMessageId(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(responseBody);
            if (root == null || !Boolean.TRUE.equals(root.getBoolean("ok"))) {
                return null;
            }
            JSONObject result = root.getJSONObject("result");
            return result == null ? null : result.getLong("message_id");
        } catch (Exception e) {
            log.warn("Extract telegram message_id failed: {}", e.getMessage());
            return null;
        }
    }

    private DecodedTelegramImage decodeImageDataUrl(String imageName, String imageDataUrl) {
        if (!StringUtils.hasText(imageDataUrl)) {
            return null;
        }
        try {
            String trimmed = imageDataUrl.trim();
            if (!trimmed.startsWith("data:")) {
                return null;
            }
            int commaIndex = trimmed.indexOf(',');
            if (commaIndex < 0) {
                return null;
            }
            String metadata = trimmed.substring(5, commaIndex);
            String base64Body = trimmed.substring(commaIndex + 1);
            String mimeType = metadata.contains(";") ? metadata.substring(0, metadata.indexOf(';')) : metadata;
            byte[] bytes = Base64.getDecoder().decode(base64Body.getBytes(StandardCharsets.UTF_8));
            String fileName = StringUtils.hasText(imageName) ? imageName.trim() : "telegram-broadcast-image";
            if (!fileName.contains(".")) {
                fileName = fileName + resolveImageExtension(mimeType);
            }
            return new DecodedTelegramImage(fileName, bytes);
        } catch (Exception e) {
            log.warn("Decode telegram image data failed: {}", e.getMessage());
            return null;
        }
    }

    private String resolveImageExtension(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return ".png";
        }
        return switch (mimeType.trim().toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private record DecodedTelegramImage(String fileName, byte[] bytes) {}

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
