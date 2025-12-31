package com.pakgopay.service.order;


import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface CollectionOrderService {

    CommonResponse createCollectionOrder(@Valid CollectionOrderRequest collectionOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String merchantOrderNo) throws PakGoPayException;
}
