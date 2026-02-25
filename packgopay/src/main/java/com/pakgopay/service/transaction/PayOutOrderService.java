package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.QueryOrderApiRequest;
import com.pakgopay.data.response.CommonResponse;

import java.util.Map;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(
            PayOutOrderRequest payOutOrderRequest, String authorization) throws PakGoPayException;

    CommonResponse queryOrderInfo(
            QueryOrderApiRequest queryRequest, String authorization) throws PakGoPayException;

    Object handleNotify(Map<String, Object> notifyData) throws PakGoPayException;

    CommonResponse queryPayOutOrders(OrderQueryRequest request) throws PakGoPayException;
}
