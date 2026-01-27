package com.pakgopay.service.transaction;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;

import java.math.BigDecimal;
import java.util.Map;

public abstract class BaseOrderService {

    protected TransactionStatus resolveNotifyStatus(String status) throws PakGoPayException {
        if (status == null || status.isBlank()) {
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "status is empty");
        }
        if (TransactionStatus.SUCCESS.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.SUCCESS;
        }
        if (TransactionStatus.FAILED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.FAILED;
        }
        if (TransactionStatus.PROCESSING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PROCESSING;
        }
        if (TransactionStatus.PENDING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PENDING;
        }
        if (TransactionStatus.EXPIRED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.EXPIRED;
        }
        if (TransactionStatus.CANCELLED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.CANCELLED;
        }
        throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "unsupported status");
    }

    protected BigDecimal resolveOrderAmount(BigDecimal actualAmount, BigDecimal amount) {
        return actualAmount != null ? actualAmount : amount;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractHandlerData(Object handlerResponse) {
        if (handlerResponse == null) {
            return null;
        }
        if (handlerResponse instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (handlerResponse instanceof PaymentHttpResponse resp) {
            Object data = resp.getData();
            if (data instanceof Map<?, ?> dataMap) {
                return (Map<String, Object>) dataMap;
            }
        }
        return null;
    }

    protected void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    protected OrderQueryEntity buildOrderQueryEntity(OrderQueryRequest request) {
        OrderQueryEntity entity = new OrderQueryEntity();
        entity.setMerchantUserId(request.getMerchantUserId());
        entity.setTransactionNo(request.getTransactionNo());
        entity.setMerchantOrderNo(request.getMerchantOrderNo());
        entity.setCurrencyType(request.getCurrency());
        entity.setOrderStatus(request.getOrderStatus());
        entity.setOrderType(request.getOrderType());
        entity.setAmount(request.getAmount());
        entity.setChannelId(request.getChannelId());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPageNo(request.getPageNo());
        entity.setPageSize(request.getPageSize());
        return entity;
    }
}
