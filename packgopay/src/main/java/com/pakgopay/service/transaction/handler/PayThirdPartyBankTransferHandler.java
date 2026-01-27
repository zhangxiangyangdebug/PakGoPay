package com.pakgopay.service.transaction.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class PayThirdPartyBankTransferHandler extends OrderHandler {

    private static final String BASE_URL = "http://localhost:8092";

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public Object handle(Object request) {
        String path = resolvePath(resolveChannelCode(request));
        Map<String, Object> payload = toPayload(request);
        validateRequiredFields(path, payload);
        String url = BASE_URL + path;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, jsonHeaders());
        log.info("PayThirdPartyBankTransferHandler handle, channelCode={}, url={}, request={}",
                resolveChannelCode(request), url, request);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        log.info("third-party payout response, channelCode={}, code={}",
                resolveChannelCode(request), response == null ? null : response.getCode());
        return response;
    }

    @Override
    public NotifyRequest handleNotify(String body) {
        Map<String, Object> payload = parseBodyMap(body);
        log.info("third-party payout notify, channelCode={}, payload={}", resolveChannelCode(payload), payload);
        return buildNotifyResponse(body);
    }

    private Map<String, Object> toPayload(Object request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        if (request instanceof Map) {
            return (Map<String, Object>) request;
        }
        return new ObjectMapper().convertValue(request, Map.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String resolveChannelCode(Object request) {
        Map<String, Object> payload = toPayload(request);
        Object raw = payload.get("channelCode");
        if (raw == null) {
            Object channelParams = payload.get("channelParams");
            if (channelParams instanceof Map<?, ?> params) {
                raw = params.get("channelCode");
            }
        }
        return raw == null ? null : String.valueOf(raw);
    }

    private void validateRequiredFields(String path, Map<String, Object> payload) {
        requireNonBlank(payload, "transactionNo");
        requirePositiveNumber(payload, "amount");
        switch (path) {
            case "/digimone/payout/create":
            case "/dana/payout/create":
            case "/nagad/payout/create":
            case "/bkash/payout/create":
                requireNonBlank(payload, "bankCode");
                requireNonBlank(payload, "accountName");
                requireNonBlank(payload, "accountNo");
                break;
            default:
                break;
        }
    }

    private void requireNonBlank(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("missing required field: " + key);
        }
    }

    private void requirePositiveNumber(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required field: " + key);
        }
        try {
            double number = Double.parseDouble(String.valueOf(value));
            if (number <= 0) {
                throw new IllegalArgumentException("invalid amount: " + key);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid number field: " + key);
        }
    }

    private String resolvePath(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            throw new IllegalArgumentException("channelCode is empty");
        }
        switch (channelCode.trim().toLowerCase()) {
            case "digimone":
                return "/digimone/payout/create";
            case "dana":
                return "/dana/payout/create";
            case "nagad":
                return "/nagad/payout/create";
            case "bkash":
                return "/bkash/payout/create";
            default:
                throw new IllegalArgumentException("unsupported channelCode: " + channelCode);
        }
    }
}
