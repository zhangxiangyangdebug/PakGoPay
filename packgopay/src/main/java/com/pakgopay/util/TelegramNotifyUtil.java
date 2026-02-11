package com.pakgopay.util;

import com.pakgopay.service.common.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
public final class TelegramNotifyUtil {

    private static volatile TelegramService telegramService;

    private TelegramNotifyUtil() {}

    static void init(TelegramService service) {
        telegramService = service;
    }

    public static boolean sendMessage(String text) {
        if (!StringUtils.hasText(text)) {
            log.warn("telegram sendMessage skipped: empty text");
            return false;
        }
        TelegramService service = telegramService;
        if (service == null) {
            log.warn("telegram sendMessage skipped: service not ready");
            return false;
        }
        if (!service.isEnabled()) {
            log.warn("telegram sendMessage skipped: disabled");
            return false;
        }
        String result = service.sendMessage(text);
        return isOk(result);
    }

    public static boolean sendMessageTo(String chatId, String text) {
        if (!StringUtils.hasText(text)) {
            log.warn("telegram sendMessageTo skipped: empty text");
            return false;
        }
        TelegramService service = telegramService;
        if (service == null) {
            log.warn("telegram sendMessageTo skipped: service not ready");
            return false;
        }
        if (!service.isEnabled()) {
            log.warn("telegram sendMessageTo skipped: disabled");
            return false;
        }
        String result = service.sendMessageTo(chatId, text);
        return isOk(result);
    }

    public static boolean sendFileTo(String chatId, String fileName, byte[] content) {
        if (!StringUtils.hasText(fileName) || content == null || content.length == 0) {
            log.warn("telegram sendFileTo skipped: invalid file");
            return false;
        }
        TelegramService service = telegramService;
        if (service == null) {
            log.warn("telegram sendFileTo skipped: service not ready");
            return false;
        }
        if (!service.isEnabled()) {
            log.warn("telegram sendFileTo skipped: disabled");
            return false;
        }
        String result = service.sendDocumentTo(chatId, fileName, content);
        return isOk(result);
    }

    private static boolean isOk(String result) {
        return result != null && result.contains("\"ok\":true");
    }

    @Component
    static class Initializer {
        Initializer(TelegramService service) {
            TelegramNotifyUtil.init(service);
        }
    }
}
