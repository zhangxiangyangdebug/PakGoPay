package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.*;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface ReportService {


    CommonResponse queryMerchantReports(MerchantReportRequest merchantReportRequest) throws PakGoPayException;

    CommonResponse queryChannelReports(@Valid ChannelReportRequest channelReportRequest) throws PakGoPayException;

    CommonResponse queryAgentReports(@Valid AgentReportRequest agentReportRequest) throws PakGoPayException;

    CommonResponse queryCurrencyReports(@Valid BaseReportRequest currencyReportRequest) throws PakGoPayException;

    CommonResponse queryPaymentReports(@Valid PaymentReportRequest paymentReportRequest) throws PakGoPayException;

    void exportMerchantReports(@Valid MerchantReportRequest merchantReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportChannelReports(@Valid ChannelReportRequest channelReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportAgentReports(@Valid AgentReportRequest agentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportCurrencyReports(@Valid BaseReportRequest currencyReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPaymentReports(@Valid PaymentReportRequest paymentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;
}
