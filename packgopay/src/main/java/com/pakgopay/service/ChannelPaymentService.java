package com.pakgopay.service;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.entity.TransactionInfo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface ChannelPaymentService {
    Long getPaymentId(
            String channelIds, Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException;

    void calculateTransactionFee(TransactionInfo transactionInfo, OrderType orderType);

    CommonResponse queryChannel(@Valid ChannelQueryRequest channelQueryRequest) throws PakGoPayException;

    CommonResponse queryPayment(@Valid PaymentQueryRequest paymentQueryRequest) throws PakGoPayException;

    void exportChannel(@Valid ChannelQueryRequest channelQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPayment(@Valid PaymentQueryRequest paymentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse editChannel(@Valid ChannelEditRequest channelEditRequest) throws PakGoPayException;

    CommonResponse editPayment(@Valid PaymentEditRequest paymentEditRequest) throws PakGoPayException;

    CommonResponse addChannel(@Valid ChannelAddRequest channelAddRequest) throws PakGoPayException;

    CommonResponse addPayment(@Valid PaymentAddRequest paymentAddRequest) throws PakGoPayException;
}
