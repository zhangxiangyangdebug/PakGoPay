package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;

import java.util.Map;

public abstract class OrderHandler {
    /**
     * Handle business logic: create/query/notify/reconcile.
     */
    public abstract Object handleCol(Object request);

    /**
     * Handle business logic: create/query/notify/reconcile.
     */
    public abstract Object handlePay(Object request);

    /**
     * Handle async notification callbacks.
     */
    public abstract NotifyRequest handleNotify(Map<String, Object> body);

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
