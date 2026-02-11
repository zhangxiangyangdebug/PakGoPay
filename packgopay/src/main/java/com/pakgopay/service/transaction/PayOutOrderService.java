package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;

import java.util.Map;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(PayOutOrderRequest payOutOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;

    String handleNotify(Map<String, Object> notifyData) throws PakGoPayException;

    CommonResponse queryPayOutOrders(OrderQueryRequest request) throws PakGoPayException;
}
