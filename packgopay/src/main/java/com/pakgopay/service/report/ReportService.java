package com.pakgopay.service.report;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface ReportService {


    CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException;

    CommonResponse queryChannelReport(@Valid ChannelReportRequest channelReportRequest) throws PakGoPayException;

    CommonResponse queryAgentReport(@Valid AgentReportRequest agentReportRequest) throws PakGoPayException;

    CommonResponse queryCurrencyReport(@Valid BaseReportRequest currencyReportRequest) throws PakGoPayException;

    CommonResponse queryPaymentReport(@Valid PaymentReportRequest paymentReportRequest) throws PakGoPayException;

    void exportMerchantReport(@Valid MerchantReportRequest merchantReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;
}
