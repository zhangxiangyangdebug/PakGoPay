package com.pakgopay.service.transaction;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.validation.Valid;

public interface PayOutOrderService {
    CommonResponse createPayOutOrder(@Valid PayOutOrderRequest payOutOrderRequest) throws PakGoPayException;

    CommonResponse queryOrderInfo(String userId, String transactionNo) throws PakGoPayException;
}
