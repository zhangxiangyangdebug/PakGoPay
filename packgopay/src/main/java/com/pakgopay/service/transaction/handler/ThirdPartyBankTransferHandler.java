package com.pakgopay.service.transaction.handler;

import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.common.OrderFlowLogSession;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ThirdPartyBankTransferHandler extends OrderHandler {

    private static final String BASE_URL = "http://localhost:8092";
    private static final String FIELD_PAY_URL = "payUrl";
    private static final String FIELD_TRANSACTION_NO = "transactionNo";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_CHANNEL_CODE = "channelCode";
    private static final String FIELD_CHANNEL_PARAMS = "channelParams";
    private static final String FIELD_MERCHANT_NAME = "merchantName";
    private static final String FIELD_MERCHANT_CITY = "merchantCity";
    private static final String FIELD_MERCHANT_ACCOUNT_NUMBER = "merchantAccountNumber";
    private static final String FIELD_PAY_IN_URL = "payInUrl";
    private static final String FIELD_CURRENCY = "currency";
    private static final String FIELD_BANK_CODE = "bankCode";
    private static final String FIELD_PAYER_MERCHANT_CARD_NO = "payermerchantCardNo";
    private static final String FIELD_PAYER_MERCHANT_NAME = "payerMerchantName";

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public PaymentHttpResponse handleCol(CollectionCreateEntity request) {
        String path = resolvePath(resolveChannelCode(request));
        Map<String, Object> payload = toPayload(request);
        validateRequiredFields(path, payload);
        String url = BASE_URL + path;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, jsonHeaders());
        log.info("ColThirdPartyBankTransferHandler handle, channelCode={}, url={}, request={}", resolveChannelCode(request), url, request);
//        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = new PaymentHttpResponse();
        response.setCode(0);
        response.setMessage("success");
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_PAY_URL,
                "https://mock-digimone.local/Transaction/Index?transactionNo="
                        + payload.get(FIELD_TRANSACTION_NO)
                        + "&amount="
                        + payload.get(FIELD_AMOUNT)
                        + "&channelCode=digimone");
        response.setData(data);
        log.info("third-party collection response, channelCode={}, code={}, response={}", resolveChannelCode(request), response == null ? null : response.getCode(), response);
        return response;
    }

    @Override
    public PaymentHttpResponse handlePay(PayCreateEntity request) {
        String path = resolvePath(resolveChannelCode(request));
        Map<String, Object> payload = toPayload(request);
        validateRequiredFields(path, payload);
        String url = BASE_URL + path;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, jsonHeaders());
        log.info("PayThirdPartyBankTransferHandler handle, channelCode={}, url={}, request={}",
                resolveChannelCode(request), url, request);
        PaymentHttpResponse rawResponse = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = new PaymentHttpResponse();
        boolean success = rawResponse != null && Integer.valueOf(200).equals(rawResponse.getCode());
        response.setCode(success ? 0 : -1);
        response.setMessage(success ? "success" : (rawResponse == null ? "request failed" : rawResponse.getMessage()));
        response.setData(rawResponse == null ? null : rawResponse.getData());
        log.info("third-party payout response, channelCode={}, code={}",
                resolveChannelCode(request), response == null ? null : response.getCode());
        return response;
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        throw new UnsupportedOperationException("collection query is not supported for ThirdPartyBankTransferHandler");
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        throw new UnsupportedOperationException("pay query is not supported for ThirdPartyBankTransferHandler");
    }

    @Override
    public BigDecimal queryPayBalance(PayCreateEntity request, OrderFlowLogSession flowSession) {
        return null;
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body, String interfaceParam) {
        log.info("third-party collection notify, channelCode={}, payload={}", resolveChannelCode(body), body);
        return buildNotifyResponse(body);
    }

    @Override
    public Object getNotifySuccessResponse() {
        return "ok";
    }

    @Override
    public NotifyResult sendNotifyToMerchant(Map<String, Object> body, String url) {
        log.info("sendNotifyToMerchant, url={}, body={}", url, body);
        return new NotifyResult(true, 0);
    }

    private Map<String, Object> toPayload(CollectionCreateEntity request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_TRANSACTION_NO, request.getTransactionNo());
        payload.put(FIELD_AMOUNT, request.getAmount());
        payload.put(FIELD_CHANNEL_CODE, request.getChannelCode());
        payload.put(FIELD_CHANNEL_PARAMS, request.getChannelParams());
        if (request.getChannelParams() != null) {
            payload.putAll(request.getChannelParams());
        }
        return payload;
    }

    private Map<String, Object> toPayload(PayCreateEntity request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_TRANSACTION_NO, request.getTransactionNo());
        payload.put(FIELD_AMOUNT, request.getAmount());
        payload.put(FIELD_CHANNEL_CODE, request.getChannelCode());
        payload.put(FIELD_CHANNEL_PARAMS, request.getChannelParams());
        if (request.getChannelParams() != null) {
            payload.putAll(request.getChannelParams());
        }
        return payload;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String resolveChannelCode(CollectionCreateEntity request) {
        Map<String, Object> payload = toPayload(request);
        Object raw = payload.get(FIELD_CHANNEL_CODE);
        if (raw == null) {
            raw = resolveChannelCodeFromParams(request == null ? null : request.getChannelParams());
        }
        return raw == null ? null : String.valueOf(raw);
    }

    private String resolveChannelCode(PayCreateEntity request) {
        Map<String, Object> payload = toPayload(request);
        Object raw = payload.get(FIELD_CHANNEL_CODE);
        if (raw == null) {
            raw = resolveChannelCodeFromParams(request == null ? null : request.getChannelParams());
        }
        return raw == null ? null : String.valueOf(raw);
    }

    private String resolveChannelCode(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get(FIELD_CHANNEL_CODE);
        if (raw == null && payload.get(FIELD_CHANNEL_PARAMS) instanceof Map<?, ?> params) {
            raw = params.get(FIELD_CHANNEL_CODE);
        }
        return raw == null ? null : String.valueOf(raw);
    }

    private Object resolveChannelCodeFromParams(Map<String, Object> channelParams) {
        if (channelParams == null) {
            return null;
        }
        return channelParams.get(FIELD_CHANNEL_CODE);
    }

    private void validateRequiredFields(String path, Map<String, Object> payload) {
        requireNonBlank(payload, FIELD_TRANSACTION_NO);
        requirePositiveNumber(payload, FIELD_AMOUNT);
        switch (path) {
            case "/digimone/collection/create":
                break;
            case "/dana/collection/create":
                break;
            case "/nagad/collection/create":
                requireNonBlank(payload, FIELD_MERCHANT_NAME);
                requireNonBlank(payload, FIELD_MERCHANT_CITY);
                requireNonBlank(payload, FIELD_MERCHANT_ACCOUNT_NUMBER);
                break;
            case "/bkash/collection/create":
                requireNonBlank(payload, FIELD_MERCHANT_NAME);
                requireNonBlank(payload, FIELD_MERCHANT_CITY);
                requireNonBlank(payload, FIELD_PAY_IN_URL);
                break;
            case "/generateqrcode/collection/create":
                requireNonBlank(payload, FIELD_CURRENCY);
                requireNonBlank(payload, FIELD_BANK_CODE);
                requireNonBlank(payload, FIELD_MERCHANT_NAME);
                requireNonBlank(payload, FIELD_MERCHANT_CITY);
                requireNonBlank(payload, FIELD_PAYER_MERCHANT_CARD_NO);
                requireNonBlank(payload, FIELD_PAYER_MERCHANT_NAME);
                break;
            default:
                break;
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
