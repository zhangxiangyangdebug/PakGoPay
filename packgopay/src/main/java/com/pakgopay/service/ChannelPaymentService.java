package com.pakgopay.service;

import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface ChannelPaymentService {
    Long selectPaymentId(Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException;

    void calculateTransactionFees(TransactionInfo transactionInfo, OrderType orderType);

    void updateChannelAndPaymentCounters(CollectionOrderDto order, TransactionStatus status);

    CommonResponse queryChannels(ChannelQueryRequest channelQueryRequest) throws PakGoPayException;

    CommonResponse queryPayments(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException;

    void exportChannels(ChannelQueryRequest channelQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPayments(PaymentQueryRequest paymentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    CommonResponse updateChannel(ChannelEditRequest channelEditRequest) throws PakGoPayException;

    CommonResponse updatePayment(PaymentEditRequest paymentEditRequest) throws PakGoPayException;

    CommonResponse createChannel(ChannelAddRequest channelAddRequest) throws PakGoPayException;

    CommonResponse createPayment(PaymentAddRequest paymentAddRequest) throws PakGoPayException;
}
