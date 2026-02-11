package com.pakgopay.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.report.OpsReportResponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.dto.OpsOrderDailyDto;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.service.OpsReportService;
import com.pakgopay.service.common.TelegramService;
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

    public WebhookController(TelegramService telegramService, OpsReportService opsReportService, CurrencyTypeMapper currencyTypeMapper) {
        this.telegramService = telegramService;
        this.opsReportService = opsReportService;
        this.currencyTypeMapper = currencyTypeMapper;
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
            JSONObject message = root.getJSONObject("message");
            if (message == null) {
                message = root.getJSONObject("channel_post");
            }
            if (message == null) {
                return CommonResponse.success("ignore");
            }
            String text = message.getString("text");
            JSONObject chat = message.getJSONObject("chat");
            String chatId = chat == null ? null : String.valueOf(chat.get("id"));

            if (text == null || chatId == null) {
                return CommonResponse.success("ignore");
            }
            if (!telegramService.isEnabled()) {
                telegramService.sendMessageTo(chatId, "未启用");
                return CommonResponse.success("disabled");
            }
            if (!isAllowedUser(message)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
            }

            String trimmed = text.trim();
            if ("/hello".equals(trimmed)) {
                telegramService.sendMessageTo(chatId, "Hello");
            } else if ("/help".equals(trimmed)) {
                telegramService.sendMessageTo(chatId, "/hello /help /todayOpsData");
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
