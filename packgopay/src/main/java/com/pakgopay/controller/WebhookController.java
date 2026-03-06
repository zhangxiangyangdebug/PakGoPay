package com.pakgopay.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.account.AccountStatementEditRequest;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.report.OpsReportResponse;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.mapper.dto.OpsOrderDailyDto;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.OpsReportService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.service.common.OrderInterventionTelegramNotifier;
import com.pakgopay.service.common.TelegramService;
import com.pakgopay.service.common.TelegramOrderNoRecognizer;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.PayOutOrderService;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * provide interface for telegram bot
 */
@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/telegram")
public class WebhookController {

    private final TelegramService telegramService;
    private final OpsReportService opsReportService;
    private final CurrencyTypeMapper currencyTypeMapper;
    private final CollectionOrderService collectionOrderService;
    private final PayOutOrderService payOutOrderService;
    private final CollectionOrderMapper collectionOrderMapper;
    private final PayOrderMapper payOrderMapper;
    private final AccountStatementsMapper accountStatementsMapper;
    private final AccountStatementService accountStatementService;
    private final RedisUtil redisUtil;
    private final OrderInterventionTelegramNotifier orderInterventionTelegramNotifier;
    private final TelegramOrderNoRecognizer telegramOrderNoRecognizer;
    private static final int WITHDRAW_PENDING_EXPIRE_SECONDS = 600;

    public WebhookController(TelegramService telegramService,
                             OpsReportService opsReportService,
                             CurrencyTypeMapper currencyTypeMapper,
                             CollectionOrderService collectionOrderService,
                             PayOutOrderService payOutOrderService,
                             CollectionOrderMapper collectionOrderMapper,
                             PayOrderMapper payOrderMapper,
                             AccountStatementsMapper accountStatementsMapper,
                             AccountStatementService accountStatementService,
                             RedisUtil redisUtil,
                             OrderInterventionTelegramNotifier orderInterventionTelegramNotifier,
                             TelegramOrderNoRecognizer telegramOrderNoRecognizer) {
        this.telegramService = telegramService;
        this.opsReportService = opsReportService;
        this.currencyTypeMapper = currencyTypeMapper;
        this.collectionOrderService = collectionOrderService;
        this.payOutOrderService = payOutOrderService;
        this.collectionOrderMapper = collectionOrderMapper;
        this.payOrderMapper = payOrderMapper;
        this.accountStatementsMapper = accountStatementsMapper;
        this.accountStatementService = accountStatementService;
        this.redisUtil = redisUtil;
        this.orderInterventionTelegramNotifier = orderInterventionTelegramNotifier;
        this.telegramOrderNoRecognizer = telegramOrderNoRecognizer;
    }

    @PostMapping
    public CommonResponse webhook(@RequestBody String payload, HttpServletRequest request, HttpServletResponse response) {
        logWebhookRequest(request, payload);
        if (!validateWebhookSecret(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
        }
        try {
            JSONObject root = JSON.parseObject(payload);
            JSONObject callbackQuery = root.getJSONObject("callback_query");
            if (callbackQuery != null) {
                return handleCallbackQuery(callbackQuery, response);
            }
            JSONObject message = root.getJSONObject("message");
            if (message == null) {
                message = root.getJSONObject("channel_post");
            }
            if (message == null) {
                return CommonResponse.success("ignore");
            }
            JSONObject chat = message.getJSONObject("chat");
            String chatId = chat == null ? null : String.valueOf(chat.get("id"));
            if (chatId == null) {
                return CommonResponse.success("ignore");
            }
            if (!telegramService.isEnabled()) {
                telegramService.sendMessageTo(chatId, "disabled");
                return CommonResponse.success("disabled");
            }
            if (!isAllowedUser(message)) {
                telegramService.sendMessageTo(chatId, "you have no permission");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
            }

            JSONObject document = message.getJSONObject("document");
            String documentImageFileId = resolveDocumentImageFileId(document);
            if (documentImageFileId != null) {
                if (!isMentionedBotForImageRecognition(message)) {
                    log.info("skip telegram image recognition: bot not mentioned");
                    return CommonResponse.success("ignore_not_mentioned");
                }
                log.info("telegram image recognition via document, fileId={}", documentImageFileId);
                handleImageOrderRecognition(message, chatId, documentImageFileId);
                return CommonResponse.success("ok");
            }

            JSONArray photos = message.getJSONArray("photo");
            if (photos != null && !photos.isEmpty()) {
                if (!isMentionedBotForImageRecognition(message)) {
                    log.info("skip telegram image recognition: bot not mentioned");
                    return CommonResponse.success("ignore_not_mentioned");
                }
                String fileId = resolveLargestPhotoFileId(photos);
                log.info("telegram image recognition via photo, fileId={}", fileId);
                handleImageOrderRecognition(message, chatId, fileId);
                return CommonResponse.success("ok");
            }

            String text = message.getString("text");
            if (text == null || text.trim().isEmpty()) {
                return CommonResponse.success("ignore");
            }
            String trimmed = text.trim();
            if (tryHandleWithdrawRemark(message, chatId, trimmed)) {
                return CommonResponse.success("ok");
            }
            if ("/hello".equals(trimmed)) {
                telegramService.sendMessageTo(chatId, "Hello");
            } else if ("/help".equals(trimmed)) {
                telegramService.sendMessageTo(chatId, "/hello /help /todayOpsData /remark 说明");
            } else if (trimmed.startsWith("/todayOpsData")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2) {
                    telegramService.sendMessageTo(chatId, buildCurrencyPrompt());
                } else {
                    String currency = parts[1].trim().toUpperCase();
                    if (!isSupportedCurrency(currency)) {
                        telegramService.sendMessageTo(chatId, "Unsupported currency: " + currency + "\n" + buildCurrencyPrompt());
                    } else {
                        byte[] excel = buildTodayOpsDataExcel(currency);
                        String targetChatId = telegramService.getDefaultChatId();
                        if (targetChatId == null || targetChatId.isEmpty()) {
                            targetChatId = chatId;
                        }
                        String fileName = "today_ops_data_" + currency + ".xlsx";
                        telegramService.sendDocumentTo(targetChatId, fileName, excel);
                        telegramService.sendMessageTo(chatId, "Ops data sent.");
                    }
                }
            } else {
                telegramService.sendMessageTo(chatId, "Unknown command. Use /help");
            }
        } catch (Exception e) {
            log.error("telegram webhook handle failed: {}", e.getMessage());
        }
        return CommonResponse.success("ok");
    }

    private void handleImageOrderRecognition(JSONObject message, String chatId, String fileId) {
        try {
            if (fileId == null) {
                telegramService.sendMessageTo(chatId, "图片读取失败，请重试。");
                return;
            }
            byte[] imageBytes = telegramService.downloadFileBytes(fileId);
            if (imageBytes == null || imageBytes.length == 0) {
                telegramService.sendMessageTo(chatId, "图片下载失败，请重试。");
                return;
            }

            String fallbackText = (message.getString("caption") == null ? "" : message.getString("caption")) + " "
                    + (message.getString("text") == null ? "" : message.getString("text"));
            String transactionNo = telegramOrderNoRecognizer.recognizeSystemOrderNo(imageBytes, fallbackText);
            if (transactionNo == null || transactionNo.isEmpty()) {
                telegramService.sendMessageTo(chatId, "未识别到系统订单号，请确保图片包含以 COLL/PAY/SE 开头的订单号。");
                return;
            }

            if (transactionNo.startsWith(CommonConstant.COLLECTION_PREFIX)) {
                handleTimeoutCollectionOrder(chatId, transactionNo);
                return;
            }
            if (transactionNo.startsWith(CommonConstant.PAYOUT_PREFIX)) {
                handleTimeoutPayoutOrder(chatId, transactionNo);
                return;
            }
            if (transactionNo.startsWith(CommonConstant.STATEMENT_PREFIX)) {
                telegramService.sendMessageTo(chatId, "已识别订单号: " + transactionNo + "，该类型不支持超时回调操作。");
                return;
            }
            telegramService.sendMessageTo(chatId, "识别到订单号: " + transactionNo + "，暂不支持该类型处理。");
        } catch (Exception e) {
            log.error("telegram photo order recognition failed: {}", e.getMessage());
            telegramService.sendMessageTo(chatId, "图片识别处理失败，请稍后重试。");
        }
    }

    private String resolveDocumentImageFileId(JSONObject document) {
        if (document == null) {
            return null;
        }
        String mimeType = document.getString("mime_type");
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return document.getString("file_id");
        }
        String fileName = document.getString("file_name");
        if (fileName != null) {
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".png")
                    || lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg")
                    || lowerName.endsWith(".webp")
                    || lowerName.endsWith(".bmp")
                    || lowerName.endsWith(".gif")) {
                return document.getString("file_id");
            }
        }
        return null;
    }

    private boolean isMentionedBotForImageRecognition(JSONObject message) {
        String botUsername = telegramService.getBotUsername();
        if (botUsername == null || botUsername.isBlank()) {
            log.warn("skip telegram image recognition: bot username not available");
            return false;
        }
        String mention = "@" + botUsername.toLowerCase(Locale.ROOT);
        String text = message.getString("text");
        String caption = message.getString("caption");
        String merged = ((caption == null ? "" : caption) + " " + (text == null ? "" : text)).toLowerCase(Locale.ROOT);
        return merged.contains(mention);
    }

    private void handleTimeoutCollectionOrder(String chatId, String transactionNo) {
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        CollectionOrderDto dto = collectionOrderMapper.findByTransactionNo(transactionNo, range[0], range[1]).orElse(null);
        if (dto == null) {
            telegramService.sendMessageTo(chatId, "未找到订单: " + transactionNo);
            return;
        }
        if (!String.valueOf(TransactionStatus.EXPIRED.getCode()).equals(dto.getOrderStatus())) {
            telegramService.sendMessageTo(chatId, "订单 " + transactionNo + " 当前非超时状态，无法触发回调操作。");
            return;
        }
        orderInterventionTelegramNotifier.notifyTimeoutCollectionOrderToChat(chatId, transactionNo, dto.getCreateTime());
    }

    private void handleTimeoutPayoutOrder(String chatId, String transactionNo) {
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        PayOrderDto dto = payOrderMapper.findByTransactionNo(transactionNo, range[0], range[1]).orElse(null);
        if (dto == null) {
            telegramService.sendMessageTo(chatId, "未找到订单: " + transactionNo);
            return;
        }
        if (!String.valueOf(TransactionStatus.EXPIRED.getCode()).equals(dto.getOrderStatus())) {
            telegramService.sendMessageTo(chatId, "订单 " + transactionNo + " 当前非超时状态，无法触发回调操作。");
            return;
        }
        orderInterventionTelegramNotifier.notifyTimeoutPayoutOrderToChat(chatId, transactionNo, dto.getCreateTime());
    }

    private String resolveLargestPhotoFileId(JSONArray photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }
        long maxSize = -1L;
        String fileId = null;
        for (int i = 0; i < photos.size(); i++) {
            JSONObject photo = photos.getJSONObject(i);
            if (photo == null) {
                continue;
            }
            long size = photo.getLongValue("file_size");
            if (size >= maxSize) {
                maxSize = size;
                fileId = photo.getString("file_id");
            }
        }
        return fileId;
    }

    private CommonResponse handleCallbackQuery(JSONObject callbackQuery, HttpServletResponse response) {
        String callbackQueryId = callbackQuery.getString("id");
        JSONObject from = callbackQuery.getJSONObject("from");
        String fromUserId = from == null ? null : String.valueOf(from.get("id"));
        if (!telegramService.isEnabled()) {
            telegramService.answerCallbackQuery(callbackQueryId, "disabled", false);
            return CommonResponse.success("disabled");
        }
        if (!telegramService.isAllowedUser(fromUserId)) {
            telegramService.answerCallbackQuery(callbackQueryId, "you have no permission", true);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
        }

        String callbackData = callbackQuery.getString("data");
        if (callbackData == null || callbackData.isBlank()) {
            telegramService.answerCallbackQuery(callbackQueryId, "invalid callback", true);
            return CommonResponse.success("ignore");
        }

        if (callbackData.startsWith("wdr|")) {
            return handleWithdrawCallbackQuery(callbackQuery, response);
        }

        String[] parts = callbackData.split("\\|");
        if (parts.length != 4 || !"ord".equals(parts[0])) {
            telegramService.answerCallbackQuery(callbackQueryId, "unsupported action", true);
            return CommonResponse.success("ignore");
        }

        String type = parts[1];
        String transactionNo = parts[2];
        String status = parts[3];
        try {
            if (!"2".equals(status) && !"3".equals(status)) {
                telegramService.answerCallbackQuery(callbackQueryId, "invalid status", true);
                return CommonResponse.success("ignore");
            }

            if ("c".equals(type)) {
                String merchantNo = getCollectionMerchantNo(transactionNo);
                if (merchantNo == null) {
                    telegramService.answerCallbackQuery(callbackQueryId, "order not found", true);
                    return CommonResponse.success("not_found");
                }
                NotifyRequest request = new NotifyRequest();
                request.setTransactionNo(transactionNo);
                request.setMerchantNo(merchantNo);
                request.setStatus(status);
                collectionOrderService.manualHandleNotify(request);
            } else if ("p".equals(type)) {
                String merchantNo = getPayoutMerchantNo(transactionNo);
                if (merchantNo == null) {
                    telegramService.answerCallbackQuery(callbackQueryId, "order not found", true);
                    return CommonResponse.success("not_found");
                }
                NotifyRequest request = new NotifyRequest();
                request.setTransactionNo(transactionNo);
                request.setMerchantNo(merchantNo);
                request.setStatus(status);
                payOutOrderService.manualHandleNotify(request);
            } else {
                telegramService.answerCallbackQuery(callbackQueryId, "unsupported order type", true);
                return CommonResponse.success("ignore");
            }

            telegramService.answerCallbackQuery(callbackQueryId, "done", false);
            updateCallbackMessage(callbackQuery, status, from);
            return CommonResponse.success("ok");
        } catch (PakGoPayException e) {
            log.error("telegram callback handle failed, code={}, message={}", e.getErrorCode(), e.getMessage());
            telegramService.answerCallbackQuery(callbackQueryId, "failed: " + e.getMessage(), true);
            return CommonResponse.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("telegram callback handle unexpected failed: {}", e.getMessage());
            telegramService.answerCallbackQuery(callbackQueryId, "failed", true);
            return CommonResponse.fail(ResultCode.FAIL, e.getMessage());
        }
    }

    private CommonResponse handleWithdrawCallbackQuery(JSONObject callbackQuery, HttpServletResponse response) {
        String callbackQueryId = callbackQuery.getString("id");
        JSONObject from = callbackQuery.getJSONObject("from");
        String fromUserId = from == null ? null : String.valueOf(from.get("id"));
        if (!telegramService.isAllowedUser(fromUserId)) {
            telegramService.answerCallbackQuery(callbackQueryId, "you have no permission", true);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
        }

        String callbackData = callbackQuery.getString("data");
        String[] parts = callbackData == null ? new String[0] : callbackData.split("\\|");
        if (parts.length != 3) {
            telegramService.answerCallbackQuery(callbackQueryId, "invalid callback", true);
            return CommonResponse.success("ignore");
        }
        String statementId = parts[1];
        String targetStatus = parts[2];
        if (!"1".equals(targetStatus) && !"2".equals(targetStatus)) {
            telegramService.answerCallbackQuery(callbackQueryId, "invalid status", true);
            return CommonResponse.success("ignore");
        }

        AccountStatementsDto dto = accountStatementsMapper.selectById(statementId);
        if (dto == null || dto.getOrderType() == null || dto.getOrderType() != 2) {
            telegramService.answerCallbackQuery(callbackQueryId, "withdraw order not found", true);
            return CommonResponse.success("not_found");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0) {
            telegramService.answerCallbackQuery(callbackQueryId, "order already processed", true);
            return CommonResponse.success("already_processed");
        }

        JSONObject pending = new JSONObject();
        pending.put("statementId", statementId);
        pending.put("status", targetStatus);
        redisUtil.setWithSecondExpire(
                buildWithdrawPendingKey(fromUserId),
                pending.toJSONString(),
                WITHDRAW_PENDING_EXPIRE_SECONDS);
        telegramService.answerCallbackQuery(callbackQueryId, "请发送说明: /remark 你的说明", false);
        telegramService.sendMessageTo(resolveCallbackChatId(callbackQuery), "请填写说明后提交，格式：/remark 你的说明");
        return CommonResponse.success("pending_remark");
    }

    private boolean tryHandleWithdrawRemark(JSONObject message, String chatId, String trimmedText) {
        if (trimmedText == null || trimmedText.isEmpty()) {
            return false;
        }
        JSONObject from = message.getJSONObject("from");
        String fromUserId = from == null ? null : String.valueOf(from.get("id"));
        if (fromUserId == null) {
            telegramService.sendMessageTo(chatId, "用户信息异常，无法处理。");
            return true;
        }
        String pendingRaw = redisUtil.getValue(buildWithdrawPendingKey(fromUserId));
        if ((pendingRaw == null || pendingRaw.isEmpty()) && !trimmedText.startsWith("/remark")) {
            return false;
        }
        if (pendingRaw == null || pendingRaw.isEmpty()) {
            telegramService.sendMessageTo(chatId, "当前没有待处理的提现审批操作。");
            return true;
        }

        String remark;
        if (trimmedText.startsWith("/remark")) {
            remark = trimmedText.replaceFirst("^/remark\\s*", "").trim();
        } else if (trimmedText.startsWith("/")) {
            telegramService.sendMessageTo(chatId, "请填写说明后提交，格式：/remark 你的说明");
            return true;
        } else {
            remark = trimmedText.trim();
        }
        if (remark.isEmpty()) {
            telegramService.sendMessageTo(chatId, "说明不能为空，请使用：/remark 你的说明");
            return true;
        }

        try {
            JSONObject pending = JSON.parseObject(pendingRaw);
            String statementId = pending.getString("statementId");
            String status = pending.getString("status");
            AccountStatementEditRequest request = new AccountStatementEditRequest();
            request.setId(statementId);
            request.setAgree("1".equals(status));
            request.setRemark(remark);
            request.setUserName("telegram:" + fromUserId);
            CommonResponse updateRes = accountStatementService.updateAccountStatement(request);
            if (updateRes != null && updateRes.getCode() != null && updateRes.getCode() == 0) {
                redisUtil.remove(buildWithdrawPendingKey(fromUserId));
                telegramService.sendMessageTo(chatId, "提现订单处理成功: " + statementId);
            } else {
                String errMsg = updateRes == null ? "unknown error" : String.valueOf(updateRes.getMessage());
                telegramService.sendMessageTo(chatId, "提现订单处理失败: " + errMsg);
            }
        } catch (Exception e) {
            log.error("telegram withdraw remark handle failed: {}", e.getMessage());
            telegramService.sendMessageTo(chatId, "提现订单处理失败，请稍后重试。");
        }
        return true;
    }

    private String buildWithdrawPendingKey(String telegramUserId) {
        return "tg:wdr:pending:" + telegramUserId;
    }

    private String resolveCallbackChatId(JSONObject callbackQuery) {
        try {
            return String.valueOf(callbackQuery.getJSONObject("message").getJSONObject("chat").get("id"));
        } catch (Exception ignored) {
            return telegramService.getDefaultChatId();
        }
    }

    private void updateCallbackMessage(JSONObject callbackQuery, String status, JSONObject from) {
        try {
            JSONObject message = callbackQuery.getJSONObject("message");
            if (message == null) {
                return;
            }
            JSONObject chat = message.getJSONObject("chat");
            if (chat == null) {
                return;
            }
            String chatId = String.valueOf(chat.get("id"));
            Long messageId = message.getLong("message_id");
            String oldText = message.getString("text");
            String operator = from == null ? "unknown" : String.valueOf(from.get("id"));
            String actionText = "2".equals(status) ? "回调成功" : "回调失败";
            String newText = (oldText == null ? "" : oldText)
                    + "\n\n处理结果: " + actionText
                    + "\n处理人: " + operator
                    + "\n处理时间: " + Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // Remove inline keyboard after processed so buttons can no longer be clicked.
            Map<String, Object> disabledReplyMarkup = new HashMap<>();
            disabledReplyMarkup.put("inline_keyboard", new ArrayList<>());
            telegramService.editMessageText(chatId, messageId, newText, disabledReplyMarkup);
        } catch (Exception e) {
            log.warn("updateCallbackMessage failed: {}", e.getMessage());
        }
    }

    private String getCollectionMerchantNo(String transactionNo) {
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        return collectionOrderMapper.findByTransactionNo(transactionNo, range[0], range[1])
                .map(CollectionOrderDto::getMerchantUserId)
                .orElse(null);
    }

    private String getPayoutMerchantNo(String transactionNo) {
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        return payOrderMapper.findByTransactionNo(transactionNo, range[0], range[1])
                .map(PayOrderDto::getMerchantUserId)
                .orElse(null);
    }

    private long[] resolveTransactionNoTimeRange(String transactionNo) {
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        if (range == null) {
            return new long[]{0L, Long.MAX_VALUE};
        }
        return range;
    }

    private boolean isAllowedUser(JSONObject message) {
        JSONObject from = message.getJSONObject("from");
        if (from == null) {
            return false;
        }
        String userId = String.valueOf(from.get("id"));
        return telegramService.isAllowedUser(userId);
    }

    private void logWebhookRequest(HttpServletRequest request, String payload) {
        try {
            String secretHeader = request.getHeader("X-Telegram-Bot-Api-Secret-Token");
            String maskedSecret = maskSecret(secretHeader);
            String userAgent = request.getHeader("User-Agent");
            String ip = request.getRemoteAddr();
            String body = payload == null ? "" : payload;
            String bodyPreview = body.length() > 800 ? body.substring(0, 800) + "...(truncated)" : body;
            log.info("telegram webhook request ip={}, ua={}, secretHeader={}", ip, userAgent, maskedSecret);
            log.info("telegram webhook payload={}", bodyPreview);
        } catch (Exception e) {
            log.warn("telegram webhook log failed: {}", e.getMessage());
        }
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "(empty)";
        }
        if (secret.length() <= 6) {
            return "***";
        }
        return secret.substring(0, 3) + "***" + secret.substring(secret.length() - 3);
    }

    private boolean validateWebhookSecret(HttpServletRequest request) {
        String secret = telegramService.getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            log.warn("Telegram webhook secret not configured.");
            return false;
        }
        String header = request.getHeader("X-Telegram-Bot-Api-Secret-Token");
        return secret.equals(header);
    }

    private String buildTodayOpsData(String currency) {
        OpsReportRequest opsRequest = new OpsReportRequest();
        opsRequest.setRecordDate(LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toEpochSecond());
        opsRequest.setCurrency(currency);
        opsRequest.setScopeType(0);
        opsRequest.setScopeId("0");
        CommonResponse response = opsReportService.queryOpsDailyReports(opsRequest);
        return JSON.toJSONString(response);
    }

    private byte[] buildTodayOpsDataExcel(String currency) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            OpsReportRequest opsRequest = new OpsReportRequest();
            opsRequest.setRecordDate(LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toEpochSecond());
            opsRequest.setCurrency(currency);
            opsRequest.setScopeType(0);
            opsRequest.setScopeId("0");
            CommonResponse response = opsReportService.queryOpsDailyReports(opsRequest);
            if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null) {
                return new byte[0];
            }
            OpsReportResponse<OpsOrderDailyDto> data = JSON.parseObject(
                response.getData(),
                new com.alibaba.fastjson2.TypeReference<OpsReportResponse<OpsOrderDailyDto>>() {}
            );

            List<OpsOrderDailyDto> collectionList = data == null || data.getCollectionList() == null
                ? List.of() : data.getCollectionList();
            List<OpsOrderDailyDto> payoutList = data == null || data.getPayoutList() == null
                ? List.of() : data.getPayoutList();

            OpsOrderDailyDto latestCollection = collectionList.stream()
                .filter(item -> item != null && item.getReportDate() != null)
                .max(Comparator.comparing(OpsOrderDailyDto::getReportDate))
                .orElse(null);
            OpsOrderDailyDto latestPayout = payoutList.stream()
                .filter(item -> item != null && item.getReportDate() != null)
                .max(Comparator.comparing(OpsOrderDailyDto::getReportDate))
                .orElse(null);

            List<String> collectingRows = buildMetricRows("代收", latestCollection);
            List<String> payingRows = buildMetricRows("代付", latestPayout);

            Map<String, Long> dailyTotalOrders = new HashMap<>();
            Map<String, BigDecimal> dailyAgentCommission = new HashMap<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            accumulateTrend(collectionList, dailyTotalOrders, dailyAgentCommission, fmt);
            accumulateTrend(payoutList, dailyTotalOrders, dailyAgentCommission, fmt);

            List<String> trendDates = dailyTotalOrders.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
            if (trendDates.size() > 5) {
                trendDates = trendDates.subList(trendDates.size() - 5, trendDates.size());
            }

            List<String> orderTrendRows = new ArrayList<>();
            List<String> commissionTrendRows = new ArrayList<>();
            Long prevOrder = null;
            BigDecimal prevCommission = null;
            for (int i = 0; i < 5; i++) {
                if (i < trendDates.size()) {
                    String date = trendDates.get(i);
                    Long orderValue = dailyTotalOrders.getOrDefault(date, 0L);
                    BigDecimal commissionValue = dailyAgentCommission.getOrDefault(date, BigDecimal.ZERO);
                    orderTrendRows.add(buildTrendRow(date, orderValue, prevOrder));
                    commissionTrendRows.add(buildTrendRow(date, commissionValue, prevCommission));
                    prevOrder = orderValue;
                    prevCommission = commissionValue;
                } else {
                    orderTrendRows.add("-");
                    commissionTrendRows.add("-");
                }
            }

            List<List<String>> head = List.of(
                List.of("代收"),
                List.of("代付"),
                List.of("订单总数趋势"),
                List.of("代理佣金趋势")
            );
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                rows.add(List.of(
                    collectingRows.get(i),
                    payingRows.get(i),
                    orderTrendRows.get(i),
                    commissionTrendRows.get(i)
                ));
            }
            EasyExcel.write(bos)
                .head(head)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("todayOpsData")
                .doWrite(rows);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("buildTodayOpsDataExcel failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private List<String> buildMetricRows(String prefix, OpsOrderDailyDto dto) {
        long orderQuantity = dto == null || dto.getOrderQuantity() == null ? 0L : dto.getOrderQuantity();
        long successQuantity = dto == null || dto.getSuccessQuantity() == null ? 0L : dto.getSuccessQuantity();
        long failQuantity = dto == null || dto.getFailQuantity() == null ? 0L : dto.getFailQuantity();
        BigDecimal successRate = dto == null || dto.getSuccessRate() == null ? BigDecimal.ZERO : dto.getSuccessRate();
        BigDecimal commission = dto == null || dto.getAgentCommission() == null ? BigDecimal.ZERO : dto.getAgentCommission();
        List<String> rows = new ArrayList<>();
        rows.add(prefix + "订单总数: " + orderQuantity);
        rows.add(prefix + "订单成功数量: " + successQuantity);
        rows.add(prefix + "订单失败数量: " + failQuantity);
        rows.add(prefix + "订单成功率: " + formatPercent(successRate));
        rows.add(prefix + "订单佣金: " + formatDecimal(commission));
        return rows;
    }

    private String buildTrendRow(String date, long value, Long prevValue) {
        return date + ": " + value + " " + trendArrow(prevValue, value);
    }

    private String buildTrendRow(String date, BigDecimal value, BigDecimal prevValue) {
        return date + ": " + formatDecimal(value) + " " + trendArrow(prevValue, value);
    }

    private String trendArrow(Long prev, long current) {
        if (prev == null) return "—";
        if (current > prev) return "↑";
        if (current < prev) return "↓";
        return "→";
    }

    private String trendArrow(BigDecimal prev, BigDecimal current) {
        if (prev == null || current == null) return "—";
        int cmp = current.compareTo(prev);
        if (cmp > 0) return "↑";
        if (cmp < 0) return "↓";
        return "→";
    }

    private void accumulateTrend(List<OpsOrderDailyDto> list,
                                 Map<String, Long> dailyTotalOrders,
                                 Map<String, BigDecimal> dailyAgentCommission,
                                 DateTimeFormatter fmt) {
        if (list == null) return;
        for (OpsOrderDailyDto item : list) {
            if (item == null || item.getReportDate() == null) continue;
            String date = Instant.ofEpochSecond(item.getReportDate()).atZone(ZoneId.of("UTC")).toLocalDate().format(fmt);
            long count = item.getOrderQuantity() == null ? 0L : item.getOrderQuantity();
            BigDecimal commission = item.getAgentCommission() == null ? BigDecimal.ZERO : item.getAgentCommission();
            dailyTotalOrders.put(date, dailyTotalOrders.getOrDefault(date, 0L) + count);
            dailyAgentCommission.put(date, dailyAgentCommission.getOrDefault(date, BigDecimal.ZERO).add(commission));
        }
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "0%";
        return value.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String buildCurrencyPrompt() {
        List<String> currencies = getAllCurrencies();
        if (currencies.isEmpty()) {
            return "No currency config.";
        }
        return "Please input currency. Example: /todayOpsData USD\nAvailable: " + String.join(", ", currencies);
    }

    private boolean isSupportedCurrency(String currency) {
        return getAllCurrencies().stream().anyMatch(item -> item.equalsIgnoreCase(currency));
    }

    private List<String> getAllCurrencies() {
        CurrencyTypeRequest request = new CurrencyTypeRequest();
        request.setAllData(true);
        request.setPageNo(1);
        request.setPageSize(1000);
        List<CurrencyTypeDTO> currencyList = currencyTypeMapper.getAllCurrencyType(request);
        return currencyList == null ? List.of()
            : currencyList.stream()
                .filter(item -> item != null && item.getCurrencyType() != null)
                .map(item -> item.getCurrencyType().toUpperCase())
                .distinct()
                .toList();
    }
}
