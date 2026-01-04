package com.pakgopay.service.order;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(@Valid PayOutOrderRequest payOutOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;
}
