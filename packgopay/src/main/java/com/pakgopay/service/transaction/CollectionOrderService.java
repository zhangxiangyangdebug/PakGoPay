package com.pakgopay.service.transaction;


import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;

import java.util.Map;

public interface CollectionOrderService {

    CommonResponse createCollectionOrder(CollectionOrderRequest collectionOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;

    String handleNotify(Map<String, Object> notifyData) throws PakGoPayException;

    CommonResponse queryCollectionOrders(OrderQueryRequest request) throws PakGoPayException;
}
