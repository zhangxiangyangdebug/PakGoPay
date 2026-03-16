package com.pakgopay.service.transaction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.common.OrderFlowLogSession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

public abstract class OrderHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    /**
     * Handle collection create request.
     */
    public abstract PaymentHttpResponse handleCol(CollectionCreateEntity request);

    /**
     * Handle payout create request.
     */
    public abstract PaymentHttpResponse handlePay(PayCreateEntity request);

    /**
     * Handle collection query request.
     */
    public abstract TransactionStatus handleCollectionQuery(CollectionQueryEntity request);

    /**
     * Handle payout query request.
     */
    public abstract TransactionStatus handlePayQuery(PayQueryEntity request);

    /**
     * Query third-party available balance before payout create.
     */
    public abstract BigDecimal queryPayBalance(PayCreateEntity request, OrderFlowLogSession flowSession);

    /**
     * Handle async notification callbacks.
     */
    public abstract NotifyRequest handleNotify(Map<String, Object> body, String interfaceParam);

    /**
     * Return provider-specific success response body for notify callback acknowledgment.
     */
    public abstract Object getNotifySuccessResponse();

    /**
     * Send notify payload to merchant callback endpoint.
     */
    public abstract NotifyResult sendNotifyToMerchant(Map<String, Object> body, String url);

    public static class NotifyResult {
        private final boolean success;
        private final int failedAttempts;

        public NotifyResult(boolean success, int failedAttempts) {
            this.success = success;
            this.failedAttempts = failedAttempts;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getFailedAttempts() {
            return failedAttempts;
        }
    }

    /**
     * Build a normalized notify response from provider payload.
     */
    protected NotifyRequest buildNotifyResponse(Map<String, Object> bodyMap) {
        String transactionNo = firstNonBlank(bodyMap,
                "transactionNo", "transaction_no","order_no");
        String merchantNo = firstNonBlank(bodyMap,
                "merchantOrderNo", "merchantNo", "merchant_order_no","mid");
        String status = firstNonBlank(bodyMap, "status");

        NotifyRequest response = new NotifyRequest();
        response.setTransactionNo(transactionNo);
        response.setMerchantNo(merchantNo);
        response.setStatus(status);
        return response;
    }

    /**
     * Validate required fields in notify response.
     */
    public static void validateNotifyResponse(NotifyRequest response) throws PakGoPayException {
        if (response == null) {
            throw new PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "notify response is empty");
        }
        if (response.getTransactionNo() == null || response.getTransactionNo().isBlank()) {
            throw new PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "transactionNo is empty");
        }
        if (response.getMerchantNo() == null || response.getMerchantNo().isBlank()) {
            throw new PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "merchantNo is empty");
        }
        if (response.getStatus() == null || response.getStatus().isBlank()) {
            throw new PakGoPayException(
                    com.pakgopay.common.enums.ResultCode.ORDER_PARAM_VALID, "status is empty");
        }
    }

    /**
     * Ensure request object is present.
     */
    protected <T> T requireRequest(T request, String message) {
        if (request == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, message);
        }
        return request;
    }

    /**
     * Ensure specified key exists and is non-blank.
     */
    protected void requireNonBlank(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "missing required field: " + key);
        }
    }

    /**
     * Resolve collection create URL from request body or channel params.
     */
    protected String resolveDirectUrl(CollectionCreateEntity payload) {
        Object value = payload.getPaymentRequestCollectionUrl();
        Map<String, Object> params = extractChannelParams(payload);
        if (value == null) {
            value = params.get("paymentRequestCollectionUrl");
        }
        if (value == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "missing required field: paymentRequestCollectionUrl");
        }
        return String.valueOf(value);
    }

    /**
     * Resolve payout create URL from request body or channel params.
     */
    protected String resolvePayUrl(PayCreateEntity payload) {
        Object value = payload.getPaymentRequestPayUrl();
        Map<String, Object> params = extractChannelParams(payload);
        if (value == null) {
            value = params.get("paymentRequestPayUrl");
        }
        if (value == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "missing required field: paymentRequestPayUrl");
        }
        return String.valueOf(value);
    }

    /**
     * Resolve collection query URL from request body or channel params.
     */
    protected String resolveCollectionQueryUrl(CollectionQueryEntity payload) {
        Object value = payload.getPaymentCheckCollectionUrl();
        Map<String, Object> params = extractChannelParams(payload);
        if (value == null) {
            value = params.get("paymentCheckCollectionUrl");
        }
        if (value == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "missing required field: paymentCheckCollectionUrl");
        }
        return String.valueOf(value);
    }

    /**
     * Resolve payout query URL from request body or channel params.
     */
    protected String resolvePayQueryUrl(PayQueryEntity payload) {
        Object value = payload.getPaymentCheckPayUrl();
        Map<String, Object> params = extractChannelParams(payload);
        if (value == null) {
            value = params.get("paymentCheckPayUrl");
        }
        if (value == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "missing required field: paymentCheckPayUrl");
        }
        return String.valueOf(value);
    }

    /**
     * Resolve payout balance query URL from request body or channel params.
     */
    protected String resolvePayBalanceUrl(PayCreateEntity payload) {
        Object value = payload.getBalanceQueryUrl();
        Map<String, Object> params = extractChannelParams(payload);
        if (value == null) {
            value = params.get("balanceQueryUrl");
        }
        if (value == null) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "missing required field: balanceQueryUrl");
        }
        return String.valueOf(value);
    }

    /**
     * Extract channel params from request entity, fallback to empty map.
     */
    protected Map<String, Object> extractChannelParams(Object payload) {
        Map<String, Object> channelParams = null;
        if (payload instanceof CollectionCreateEntity entity) {
            channelParams = entity.getChannelParams();
        } else if (payload instanceof PayCreateEntity entity) {
            channelParams = entity.getChannelParams();
        } else if (payload instanceof CollectionQueryEntity entity) {
            channelParams = parseCollectionQueryInterfaceParams(entity.getCollectionInterfaceParam());
        } else if (payload instanceof PayQueryEntity entity) {
            channelParams = parsePayQueryInterfaceParams(entity.getPayInterfaceParam());
        }
        return channelParams == null ? Collections.emptyMap() : channelParams;
    }

    private Map<String, Object> parseCollectionQueryInterfaceParams(String interfaceParam) {
        if (interfaceParam == null || interfaceParam.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(interfaceParam, MAP_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parsePayQueryInterfaceParams(String interfaceParam) {
        if (interfaceParam == null || interfaceParam.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(interfaceParam, MAP_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Resolve value by key with fallback.
     */
    protected Object resolveValue(Map<String, Object> params, String key, Object fallback) {
        Object value = params.get(key);
        return value == null ? fallback : value;
    }

    /**
     * Normalize amount to 2-decimal plain string.
     */
    protected String resolveAmountString(Object amount) {
        if (amount == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(amount))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (Exception e) {
            return String.valueOf(amount);
        }
    }

    /**
     * Return first non-blank value by candidate keys.
     */
    protected String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
