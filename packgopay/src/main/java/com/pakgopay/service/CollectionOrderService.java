package com.pakgopay.service;


import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface CollectionOrderService {

    public CommonResponse createCollectionOrder(@Valid CollectionOrderRequest collectionOrderRequest);
}
