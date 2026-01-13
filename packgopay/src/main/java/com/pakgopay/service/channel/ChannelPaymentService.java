package com.pakgopay.service.channel;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.channel.ChannelEditRequest;
import com.pakgopay.common.reqeust.channel.ChannelQueryRequest;
import com.pakgopay.common.reqeust.channel.PaymentEditRequest;
import com.pakgopay.common.reqeust.channel.PaymentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.entity.TransactionInfo;
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
}
