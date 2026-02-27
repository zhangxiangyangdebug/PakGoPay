package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.reqeust.transaction.QueryBalanceApiRequest;
import com.pakgopay.data.reqeust.transaction.QueryOrderApiRequest;
import com.pakgopay.data.response.CommonResponse;

import java.util.Map;

public interface CollectionOrderService {

    CommonResponse createCollectionOrder(
            CollectionOrderRequest collectionOrderRequest, String authorization) throws PakGoPayException;

    CommonResponse manualCreateCollectionOrder(
            CollectionOrderRequest collectionOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(
            QueryOrderApiRequest queryRequest, String authorization) throws PakGoPayException;

    CommonResponse queryBalance(
            QueryBalanceApiRequest queryRequest, String authorization) throws PakGoPayException;

    Object handleNotify(Map<String, Object> notifyData) throws PakGoPayException;

    CommonResponse manualHandleNotify(NotifyRequest notifyRequest) throws PakGoPayException;

    CommonResponse queryCollectionOrders(OrderQueryRequest request) throws PakGoPayException;
}
