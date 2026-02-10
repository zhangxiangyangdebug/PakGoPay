package com.pakgopay.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
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
import java.util.List;

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
        if (!validateWebhookSecret(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, "forbidden");
        }
        try {
            JSONObject root = JSON.parseObject(payload);
            JSONObject message = root.getJSONObject("message");
            if (message == null) {
                return CommonResponse.success("ignore");
            }
            String text = message.getString("text");
            JSONObject chat = message.getJSONObject("chat");
            String chatId = chat == null ? null : String.valueOf(chat.get("id"));

            if (text == null || chatId == null) {
                return CommonResponse.success("ignore");
            }

            switch (text.trim()) {
                case "/hello":
                    telegramService.sendMessageTo(chatId, "Hello");
                    break;
                case "/help":
                    telegramService.sendMessageTo(chatId, "/hello /help /todayOpsData");
                    break;
                case "/todayOpsData":
                    String reply = buildTodayOpsData();
                    telegramService.sendMessageTo(chatId, reply);
                    break;
                default:
                    telegramService.sendMessageTo(chatId, "Unknown command. Use /help");
                    break;
            }
        } catch (Exception e) {
            log.error("telegram webhook handle failed: {}", e.getMessage());
        }
        return CommonResponse.success("ok");
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

    private String buildTodayOpsData() {
        CurrencyTypeRequest request = new CurrencyTypeRequest();
        request.setAllData(true);
        request.setPageNo(1);
        request.setPageSize(1);
        List<CurrencyTypeDTO> currencyList = currencyTypeMapper.getAllCurrencyType(request);
        if (currencyList == null || currencyList.isEmpty() || currencyList.get(0) == null) {
            return "No currency config.";
        }
        String currency = currencyList.get(0).getCurrencyType();
        OpsReportRequest opsRequest = new OpsReportRequest();
        opsRequest.setRecordDate(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
        opsRequest.setCurrency(currency);
        opsRequest.setScopeType(0);
        opsRequest.setScopeId("0");
        CommonResponse response = opsReportService.queryOpsDailyReports(opsRequest);
        return JSON.toJSONString(response);
    }
}
