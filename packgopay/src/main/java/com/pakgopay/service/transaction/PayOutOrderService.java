package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(PayOutOrderRequest payOutOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;

    CommonResponse handleNotify(String currency, String body) throws PakGoPayException;

    CommonResponse queryPayOutOrders(OrderQueryRequest request) throws PakGoPayException;
}
