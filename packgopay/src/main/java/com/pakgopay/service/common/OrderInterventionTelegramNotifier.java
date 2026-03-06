package com.pakgopay.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OrderInterventionTelegramNotifier {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CALLBACK_PREFIX = "ord";
    private static final String WITHDRAW_CALLBACK_PREFIX = "wdr";

    private final TelegramService telegramService;

    public OrderInterventionTelegramNotifier(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    public void notifyTimeoutCollectionOrder(String transactionNo, Long createTime) {
        sendTimeoutOrder(null, "collection", transactionNo, createTime);
    }

    public void notifyTimeoutPayoutOrder(String transactionNo, Long createTime) {
        sendTimeoutOrder(null, "payout", transactionNo, createTime);
    }

    public void notifyTimeoutCollectionOrderToChat(String chatId, String transactionNo, Long createTime) {
        sendTimeoutOrder(chatId, "collection", transactionNo, createTime);
    }

    public void notifyTimeoutPayoutOrderToChat(String chatId, String transactionNo, Long createTime) {
        sendTimeoutOrder(chatId, "payout", transactionNo, createTime);
    }

    public void notifyPendingWithdrawOrder(String statementId, Long createTime) {
        if (!telegramService.isEnabled()) {
            log.info("skip telegram withdraw notify: telegram disabled, statementId={}", statementId);
            return;
        }
        if (!StringUtils.hasText(statementId)) {
            return;
        }
        String chatId = telegramService.getDefaultChatId();
        if (!StringUtils.hasText(chatId)) {
            log.warn("skip telegram withdraw notify: default chatId is empty");
            return;
        }
        String createTimeText = createTime == null
                ? "-"
                : Instant.ofEpochSecond(createTime).atZone(ZoneId.systemDefault()).format(TIME_FMT);
        String text = "【需审批】提现订单\n"
                + "订单号: " + statementId + "\n"
                + "创建时间: " + createTimeText + "\n"
                + "请选择操作(需填写说明):";
        Map<String, Object> markup = buildWithdrawReplyMarkup(statementId);
        try {
            telegramService.sendMessageWithInlineKeyboardTo(chatId, text, markup);
        } catch (Exception e) {
            log.warn("telegram withdraw notify failed, statementId={}, chatId={}, message={}",
                    statementId, chatId, e.getMessage());
        }
    }

    private void sendTimeoutOrder(String targetChatId, String orderType, String transactionNo, Long createTime) {
        if (!telegramService.isEnabled()) {
            log.info("skip telegram intervention notify: telegram disabled, orderType={}, transactionNo={}", orderType, transactionNo);
            return;
        }
        if (!StringUtils.hasText(transactionNo)) {
            return;
        }
        String chatId = StringUtils.hasText(targetChatId) ? targetChatId : telegramService.getDefaultChatId();
        if (!StringUtils.hasText(chatId)) {
            log.warn("skip telegram intervention notify: default chatId is empty");
            return;
        }

        String typeLabel = "collection".equals(orderType) ? "代收超时订单" : "代付超时订单";
        String createTimeText = createTime == null
                ? "-"
                : Instant.ofEpochSecond(createTime).atZone(ZoneId.systemDefault()).format(TIME_FMT);
        String text = "【需人工处理】" + typeLabel + "\n"
                + "订单号: " + transactionNo + "\n"
                + "创建时间: " + createTimeText + "\n"
                + "请选择操作:";

        Map<String, Object> markup = buildReplyMarkup(orderType, transactionNo);
        telegramService.sendMessageWithInlineKeyboardTo(chatId, text, markup);
    }

    private Map<String, Object> buildReplyMarkup(String orderType, String transactionNo) {
        String shortType = "collection".equals(orderType) ? "c" : "p";
        List<List<Map<String, String>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, String>> row1 = new ArrayList<>();
        row1.add(buttonCallback("回调成功", CALLBACK_PREFIX + "|" + shortType + "|" + transactionNo + "|2"));
        row1.add(buttonCallback("回调失败", CALLBACK_PREFIX + "|" + shortType + "|" + transactionNo + "|3"));
        inlineKeyboard.add(row1);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", inlineKeyboard);
        return replyMarkup;
    }

    private Map<String, Object> buildWithdrawReplyMarkup(String statementId) {
        List<List<Map<String, String>>> inlineKeyboard = new ArrayList<>();
        List<Map<String, String>> row1 = new ArrayList<>();
        row1.add(buttonCallback("通过", WITHDRAW_CALLBACK_PREFIX + "|" + statementId + "|1"));
        row1.add(buttonCallback("驳回", WITHDRAW_CALLBACK_PREFIX + "|" + statementId + "|2"));
        inlineKeyboard.add(row1);

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

}
