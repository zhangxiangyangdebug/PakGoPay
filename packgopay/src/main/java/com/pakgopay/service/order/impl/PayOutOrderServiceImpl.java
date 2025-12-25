package com.pakgopay.service.order.impl;

import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.order.PayOutOrderService;
import org.springframework.stereotype.Service;

@Service
public class PayOutOrderServiceImpl implements PayOutOrderService {

    @Override
    public CommonResponse createPayOutOrder(PayOutOrderRequest payOutOrderRequest) {
        return null;
    }
}
