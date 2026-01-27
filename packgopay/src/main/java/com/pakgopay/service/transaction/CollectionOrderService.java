package com.pakgopay.service.transaction;


import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface CollectionOrderService {

    CommonResponse createCollectionOrder(CollectionOrderRequest collectionOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;

    CommonResponse handleNotify(String currency, String body) throws PakGoPayException;

    CommonResponse queryCollectionOrders(OrderQueryRequest request) throws PakGoPayException;
}
