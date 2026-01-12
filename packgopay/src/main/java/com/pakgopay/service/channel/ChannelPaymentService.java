package com.pakgopay.service.channel;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.channel.ChannelRequest;
import com.pakgopay.common.reqeust.channel.PaymentRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.entity.TransactionInfo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface ChannelPaymentService {
    Long getPaymentId(
            String channelIds, Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException;

    void calculateTransactionFee(TransactionInfo transactionInfo, OrderType orderType);

    CommonResponse queryChannel(@Valid ChannelRequest channelRequest) throws PakGoPayException;

    CommonResponse queryPayment(@Valid PaymentRequest paymentRequest) throws PakGoPayException;

    void exportChannel(@Valid ChannelRequest channelRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPayment(@Valid PaymentRequest paymentRequest, HttpServletResponse response) throws PakGoPayException, IOException;
}
