package com.pakgopay.service.transaction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;

import java.util.HashMap;
import java.util.Map;

public abstract class OrderHandler {
    /**
     * Handle business logic: create/query/notify/reconcile.
     */
    public abstract Object handle(Object request);

    /**
     * Handle async notification callbacks.
     */
    public abstract NotifyRequest handleNotify(String body);

    protected NotifyRequest buildNotifyResponse(String body) {
        Map<String, Object> bodyMap = parseBodyMap(body);
        String transactionNo = firstNonBlank(bodyMap,
                "transactionNo", "transaction_no");
        String merchantNo = firstNonBlank(bodyMap,
                "merchantOrderNo", "merchantNo", "merchant_order_no");
        String status = firstNonBlank(bodyMap, "status");

        NotifyRequest response = new NotifyRequest();
        response.setTransactionNo(transactionNo);
        response.setMerchantNo(merchantNo);
        response.setStatus(status);
        return response;
    }

    protected Map<String, Object> parseBodyMap(String body) {
        return parseJsonToMap(body);
    }

    public static void validateNotifyResponse(NotifyRequest response) throws com.pakgopay.common.exception.PakGoPayException {
        if (response == null) {
            throw new com.pakgopay.common.exception.PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "notify response is empty");
        }
        if (response.getTransactionNo() == null || response.getTransactionNo().isBlank()) {
            throw new com.pakgopay.common.exception.PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "transactionNo is empty");
        }
        if (response.getMerchantNo() == null || response.getMerchantNo().isBlank()) {
            throw new com.pakgopay.common.exception.PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "merchantNo is empty");
        }
        if (response.getStatus() == null || response.getStatus().isBlank()) {
            throw new com.pakgopay.common.exception.PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "status is empty");
        }
    }

    private Map<String, Object> parseJsonToMap(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new ObjectMapper().readValue(bodyText, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
