package com.pakgopay.service.order;

import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(@Valid PayOutOrderRequest payOutOrderRequest);
}
