package com.pakgopay.service.report;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.*;
import com.pakgopay.data.response.CommonResponse;
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

    void exportChannelReport(@Valid ChannelReportRequest channelReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportAgentReport(@Valid AgentReportRequest agentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportCurrencyReport(@Valid BaseReportRequest currencyReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPaymentReport(@Valid PaymentReportRequest paymentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;
}
