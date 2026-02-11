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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ThirdPartyBankTransferHandler extends OrderHandler {

    private static final String BASE_URL = "http://localhost:8092";

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public Object handleCol(Object request) {
        String path = resolvePath(resolveChannelCode(request));
        Map<String, Object> payload = toPayload(request);
        validateRequiredFields(path, payload);
        String url = BASE_URL + path;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, jsonHeaders());
        log.info("ColThirdPartyBankTransferHandler handle, channelCode={}, url={}, request={}", resolveChannelCode(request), url, request);
//        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = new PaymentHttpResponse();
        response.setCode(200);
        response.setMessage("success");
        Map<String, Object> data = new HashMap<>();
        data.put("payUrl",
                "https://mock-digimone.local/Transaction/Index?transactionNo="
                        + payload.get("transactionNo")
                        + "&amount="
                        + payload.get("amount")
                        + "&channelCode=digimone");
        response.setData(data);
        log.info("third-party collection response, channelCode={}, code={}, response={}", resolveChannelCode(request), response == null ? null : response.getCode(), response);
        return response;
    }

    @Override
    public Object handlePay(Object request) {
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
    public NotifyRequest handleNotify(Map<String, Object> body) {
        log.info("third-party collection notify, channelCode={}, payload={}", resolveChannelCode(body), body);
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
            case "/digimone/collection/create":
                break;
            case "/dana/collection/create":
                break;
            case "/nagad/collection/create":
                requireNonBlank(payload, "merchantName");
                requireNonBlank(payload, "merchantCity");
                requireNonBlank(payload, "merchantAccountNumber");
                break;
            case "/bkash/collection/create":
                requireNonBlank(payload, "merchantName");
                requireNonBlank(payload, "merchantCity");
                requireNonBlank(payload, "payInUrl");
                break;
            case "/generateqrcode/collection/create":
                requireNonBlank(payload, "currency");
                requireNonBlank(payload, "bankCode");
                requireNonBlank(payload, "merchantName");
                requireNonBlank(payload, "merchantCity");
                requireNonBlank(payload, "payermerchantCardNo");
                requireNonBlank(payload, "payerMerchantName");
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
                return "/digimone/collection/create";
            case "dana":
                return "/dana/collection/create";
            case "nagad":
                return "/nagad/collection/create";
            case "bkash":
                return "/bkash/collection/create";
            case "generateqrcode":
            case "generate_qrcode":
                return "/generateqrcode/collection/create";
            default:
                throw new IllegalArgumentException("unsupported channelCode: " + channelCode);
        }
    }
}
