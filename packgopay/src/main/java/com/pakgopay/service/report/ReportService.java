package com.pakgopay.service.report;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.Valid;

public interface ReportService {


    CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException;

    CommonResponse queryChannelReport(@Valid ChannelReportRequest channelReportRequest) throws PakGoPayException;

    CommonResponse queryAgentReport(@Valid AgentReportRequest agentReportRequest) throws PakGoPayException;

    CommonResponse queryCurrencyReport(@Valid BaseReportRequest currencyReportRequest) throws PakGoPayException;

    CommonResponse queryPaymentReport(@Valid PaymentReportRequest paymentReportRequest) throws PakGoPayException;
}
