package com.pakgopay.service.transaction;


import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.validation.Valid;

public interface CollectionOrderService {

    CommonResponse createCollectionOrder(@Valid CollectionOrderRequest collectionOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;

    CommonResponse handleNotify(String currency, String body) throws PakGoPayException;
}
