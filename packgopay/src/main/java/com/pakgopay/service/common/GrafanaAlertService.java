package com.pakgopay.service.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
import com.pakgopay.thirdUtil.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class GrafanaAlertService {

    private static final int ALERT_DEDUPE_SECONDS = 300;
    private static final int MUTE_5_MINUTES_SECONDS = 300;
    private static final int MAX_ALERT_LINES = 3;
    private static final int MAX_TELEGRAM_TEXT_LENGTH = 3900;

    private final TelegramService telegramService;
    private final SystemConfigGroupService systemConfigGroupService;
    private final RedisUtil redisUtil;

    public GrafanaAlertService(TelegramService telegramService,
                               SystemConfigGroupService systemConfigGroupService,
                               RedisUtil redisUtil) {
        this.telegramService = telegramService;
        this.systemConfigGroupService = systemConfigGroupService;
        this.redisUtil = redisUtil;
    }

    public ProcessResult process(String payload, HttpServletRequest request) {
        if (!telegramService.isEnabled()) {
            return ProcessResult.ok("telegram disabled");
        }
        String expectedToken = readConfig(SystemConfigItemKeyEnum.TELEGRAM_WEBHOOK_SECRET);
        if (!StringUtils.hasText(expectedToken)) {
            return ProcessResult.unauthorized("telegram webhookSecret is empty");
        }
        String providedToken = resolveWebhookToken(request);
        if (!expectedToken.equals(providedToken)) {
            return ProcessResult.unauthorized("invalid webhook token");
        }
        String chatId = readConfig(SystemConfigItemKeyEnum.TELEGRAM_ALERT_CHAT_ID);
        if (!StringUtils.hasText(chatId)) {
            return ProcessResult.invalid("telegram.alertChatId is empty");
        }

        JSONObject root;
        try {
            root = JSON.parseObject(payload);
        } catch (Exception e) {
            return ProcessResult.invalid("invalid grafana payload");
        }
        if (root == null || root.isEmpty()) {
            return ProcessResult.invalid("empty grafana payload");
        }

        String alertClassKey = buildAlertClassKey(root);
        if (StringUtils.hasText(redisUtil.getValue(buildMuteKey(alertClassKey)))) {
            return ProcessResult.ok("alert muted by quick action");
        }

        String dedupeKey = buildDedupeKey(root);
        boolean first = redisUtil.setIfAbsentWithSecondExpire(dedupeKey, "1", ALERT_DEDUPE_SECONDS);
        if (!first) {
            return ProcessResult.ok("duplicated alert muted");
        }

        String text = buildTelegramText(root);
        Map<String, Object> markup = buildQuickMuteReplyMarkup(alertClassKey);
        String result = telegramService.sendMessageWithInlineKeyboardTo(chatId.trim(), text, markup);
        if (result == null || !result.contains("\"ok\":true")) {
            return ProcessResult.error("telegram send failed");
        }
        return ProcessResult.ok("sent");
    }

    private String buildTelegramText(JSONObject root) {
        String status = firstNonBlank(root.getString("status"), "unknown");
        String title = firstNonBlank(root.getString("title"), root.getString("ruleName"), "Grafana Alert");
        String message = firstNonBlank(root.getString("message"), "-");
        String ruleUrl = firstNonBlank(root.getString("ruleUrl"), root.getString("externalURL"), "-");
        JSONObject labels = root.getJSONObject("commonLabels");
        JSONArray alerts = root.getJSONArray("alerts");

        StringBuilder sb = new StringBuilder();
        sb.append("【Grafana告警】").append(status.toUpperCase(Locale.ROOT)).append('\n');
        sb.append("标题: ").append(title).append('\n');
        appendLabelLine(sb, labels, "alertname", "告警名");
        appendLabelLine(sb, labels, "severity", "级别");
        appendLabelLine(sb, labels, "instance", "实例");
        appendLabelLine(sb, labels, "job", "任务");
        appendLabelLine(sb, labels, "service", "服务");
        appendLabelLine(sb, labels, "env", "环境");
        appendLabelLine(sb, labels, "category", "分类");
        appendLabelLine(sb, labels, "errorCode", "错误码");
        appendLabelLine(sb, labels, "bizType", "业务类型");
        appendLabelLine(sb, labels, "api", "接口");
        appendLabelLine(sb, labels, "reason", "原因");
        appendLabelLine(sb, labels, "mapper", "Mapper");
        appendLabelLine(sb, labels, "threshold", "阈值");
        appendLabelLine(sb, labels, "rootException", "异常");
        appendLabelLine(sb, labels, "logger", "日志器");
        sb.append("说明: ").append(message).append('\n');

        if (alerts != null && !alerts.isEmpty()) {
            sb.append("样本:\n");
            int size = Math.min(alerts.size(), MAX_ALERT_LINES);
            for (int i = 0; i < size; i++) {
                JSONObject alert = alerts.getJSONObject(i);
                if (alert == null) {
                    continue;
                }
                JSONObject alertLabels = alert.getJSONObject("labels");
                JSONObject annotations = alert.getJSONObject("annotations");
                String alertName = safeLabel(alertLabels, "alertname");
                String instance = safeLabel(alertLabels, "instance");
                String summary = firstNonBlank(
                        annotations == null ? null : annotations.getString("summary"),
                        annotations == null ? null : annotations.getString("description"),
                        "-");
                String startsAt = firstNonBlank(alert.getString("startsAt"), "-");
                sb.append(i + 1).append(". ");
                sb.append(alertName).append(" @ ").append(instance);
                sb.append(" | ").append(summary);
                sb.append(" | ").append(startsAt).append('\n');
            }
        }
        sb.append("链接: ").append(ruleUrl);
        String raw = sb.toString();
        if (raw.length() <= MAX_TELEGRAM_TEXT_LENGTH) {
            return raw;
        }
        return raw.substring(0, MAX_TELEGRAM_TEXT_LENGTH) + "...";
    }

    private void appendLabelLine(StringBuilder sb, JSONObject labels, String key, String label) {
        String value = safeLabel(labels, key);
        if (StringUtils.hasText(value)) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private String buildDedupeKey(JSONObject root) {
        String status = firstNonBlank(root.getString("status"), "unknown");
        String alertClassKey = buildAlertClassKey(root);
        JSONArray alerts = root.getJSONArray("alerts");
        String fingerprint = null;
        if (alerts != null && !alerts.isEmpty()) {
            JSONObject first = alerts.getJSONObject(0);
            if (first != null) {
                fingerprint = first.getString("fingerprint");
            }
        }
        String source = alertClassKey
                + "|" + firstNonBlank(fingerprint, "-")
                + "|" + status;
        return "grafana:alert:dedupe:" + sha1Hex(source);
    }

    private String buildAlertClassKey(JSONObject root) {
        JSONObject commonLabels = root.getJSONObject("commonLabels");
        String alertName = safeLabel(commonLabels, "alertname");
        String severity = safeLabel(commonLabels, "severity");
        String instance = safeLabel(commonLabels, "instance");
        String job = safeLabel(commonLabels, "job");
        String category = safeLabel(commonLabels, "category");
        String errorCode = safeLabel(commonLabels, "errorCode");
        String bizType = safeLabel(commonLabels, "bizType");
        String api = safeLabel(commonLabels, "api");
        String reason = safeLabel(commonLabels, "reason");
        String mapper = safeLabel(commonLabels, "mapper");
        String threshold = safeLabel(commonLabels, "threshold");
        String rootException = safeLabel(commonLabels, "rootException");
        String logger = safeLabel(commonLabels, "logger");
        String source = firstNonBlank(alertName, root.getString("title"), root.getString("ruleName"), "unknown")
                + "|" + firstNonBlank(severity, "-")
                + "|" + firstNonBlank(instance, "-")
                + "|" + firstNonBlank(job, "-")
                + "|" + firstNonBlank(category, "-")
                + "|" + firstNonBlank(errorCode, "-")
                + "|" + firstNonBlank(bizType, "-")
                + "|" + firstNonBlank(api, "-")
                + "|" + firstNonBlank(reason, "-")
                + "|" + firstNonBlank(mapper, "-")
                + "|" + firstNonBlank(threshold, "-")
                + "|" + firstNonBlank(rootException, "-")
                + "|" + firstNonBlank(logger, "-");
        return sha1Hex(source);
    }

    private String buildMuteKey(String alertClassKey) {
        return "grafana:alert:mute:" + alertClassKey;
    }

    private Map<String, Object> buildQuickMuteReplyMarkup(String alertClassKey) {
        List<List<Map<String, String>>> inlineKeyboard = new ArrayList<>();
        List<Map<String, String>> row = new ArrayList<>();
        row.add(buttonCallback("5分钟屏蔽同类告警", "gal|m5|" + alertClassKey));
        row.add(buttonCallback("当天屏蔽同类告警", "gal|d1|" + alertClassKey));
        inlineKeyboard.add(row);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", inlineKeyboard);
        return replyMarkup;
    }

    private Map<String, String> buttonCallback(String text, String callbackData) {
        Map<String, String> button = new HashMap<>();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }

    public ProcessResult applyMuteAction(String action, String alertClassKey) {
        if (!StringUtils.hasText(alertClassKey)) {
            return ProcessResult.invalid("invalid alert class");
        }
        if ("m5".equals(action)) {
            redisUtil.setWithSecondExpire(buildMuteKey(alertClassKey), "1", MUTE_5_MINUTES_SECONDS);
            return ProcessResult.ok("已屏蔽同类告警 5 分钟");
        }
        if ("d1".equals(action)) {
            int seconds = secondsUntilTodayEnd();
            redisUtil.setWithSecondExpire(buildMuteKey(alertClassKey), "1", seconds);
            return ProcessResult.ok("已屏蔽同类告警至今日结束");
        }
        return ProcessResult.invalid("unsupported mute action");
    }

    private int secondsUntilTodayEnd() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime end = now.toLocalDate().atTime(LocalTime.MAX);
        long seconds = now.until(end, ChronoUnit.SECONDS);
        if (seconds <= 0) {
            return 60;
        }
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private String sha1Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            Formatter formatter = new Formatter();
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            String value = formatter.toString();
            formatter.close();
            return value;
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    private String safeLabel(JSONObject labels, String key) {
        if (labels == null || !StringUtils.hasText(key)) {
            return "";
        }
        String value = labels.getString(key);
        return value == null ? "" : value.trim();
    }

    private String resolveWebhookToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader("X-Grafana-Token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        token = request.getHeader("X-Webhook-Token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private String readConfig(SystemConfigItemKeyEnum key) {
        try {
            String value = systemConfigGroupService.getConfigValue(key, String.class);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("read telegram config failed, key={}, message={}", key, e.getMessage());
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static class ProcessResult {
        private final int httpStatus;
        private final boolean success;
        private final String message;

        private ProcessResult(int httpStatus, boolean success, String message) {
            this.httpStatus = httpStatus;
            this.success = success;
            this.message = message;
        }

        public static ProcessResult ok(String message) {
            return new ProcessResult(200, true, message);
        }

        public static ProcessResult invalid(String message) {
            return new ProcessResult(400, false, message);
        }

        public static ProcessResult unauthorized(String message) {
            return new ProcessResult(401, false, message);
        }

        public static ProcessResult error(String message) {
            return new ProcessResult(500, false, message);
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> asResponsePayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", success);
            payload.put("message", message);
            return payload;
        }
    }
}
