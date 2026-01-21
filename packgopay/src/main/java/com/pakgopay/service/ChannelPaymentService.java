package com.pakgopay.service;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface ChannelPaymentService {
    Long getPaymentId(Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException;

    void calculateTransactionFee(TransactionInfo transactionInfo, OrderType orderType);

    CommonResponse queryChannel(ChannelQueryRequest channelQueryRequest) throws PakGoPayException;

    CommonResponse queryPayment(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException;

    void exportChannel(ChannelQueryRequest channelQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPayment(PaymentQueryRequest paymentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse editChannel(ChannelEditRequest channelEditRequest) throws PakGoPayException;

    CommonResponse editPayment(PaymentEditRequest paymentEditRequest) throws PakGoPayException;

    CommonResponse addChannel(ChannelAddRequest channelAddRequest) throws PakGoPayException;

    CommonResponse addPayment(PaymentAddRequest paymentAddRequest) throws PakGoPayException;
}
