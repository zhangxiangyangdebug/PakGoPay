package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.*;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface ReportService {


    CommonResponse queryMerchantReports(MerchantReportRequest merchantReportRequest) throws PakGoPayException;

    CommonResponse queryChannelReports(ChannelReportRequest channelReportRequest) throws PakGoPayException;

    CommonResponse queryAgentReports(AgentReportRequest agentReportRequest) throws PakGoPayException;

    CommonResponse queryCurrencyReports(BaseReportRequest currencyReportRequest) throws PakGoPayException;

    CommonResponse queryPaymentReports(PaymentReportRequest paymentReportRequest) throws PakGoPayException;

    void exportMerchantReports(MerchantReportRequest merchantReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportChannelReports(ChannelReportRequest channelReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportAgentReports(AgentReportRequest agentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportCurrencyReports(BaseReportRequest currencyReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;

    void exportPaymentReports(PaymentReportRequest paymentReportRequest, HttpServletResponse response) throws PakGoPayException, IOException;
}
