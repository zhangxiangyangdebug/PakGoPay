package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.ReportService;
import com.pakgopay.util.ExportFileUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class ReportController {

    @Autowired
    ReportService reportService;

    //-------------------------------- query data ----------------------------------------------------------------------

    @PostMapping(value = "queryMerchantReport")
    public CommonResponse queryMerchantReport(@RequestBody @Valid MerchantReportRequest merchantReportRequest) {
        try {
            return reportService.queryMerchantReports(merchantReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchantReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchantReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryChannelReport")
    public CommonResponse queryChannelReport(@RequestBody @Valid ChannelReportRequest channelReportRequest) {
        try {
            return reportService.queryChannelReports(channelReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryChannelReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryChannelReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryAgentReport")
    public CommonResponse queryAgentReport(@RequestBody @Valid AgentReportRequest agentReportRequest) {
        try {
            return reportService.queryAgentReports(agentReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryAgentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryAgentReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryCurrencyReport")
    public CommonResponse queryCurrencyReport(@RequestBody @Valid BaseReportRequest currencyReportRequest) {
        try {
            return reportService.queryCurrencyReports(currencyReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryCurrencyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryCurrencyReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryPaymentReport")
    public CommonResponse queryPaymentReport(@RequestBody @Valid PaymentReportRequest paymentReportRequest) {
        try {
            return reportService.queryPaymentReports(paymentReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryPaymentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryPaymentReport failed: " + e.getMessage());
        }
    }

    //-------------------------------- export data ---------------------------------------------------------------------
    @PostMapping(value = "exportMerchantReport")
    public void exportMerchantReport(
            @RequestBody @Valid MerchantReportRequest merchantReportRequest, HttpServletResponse response) {
        try {
            reportService.exportMerchantReports(merchantReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportMerchantReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportMerchantReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportMerchantReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportMerchantReport failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportChannelReport")
    public void exportChannelReport(
            @RequestBody @Valid ChannelReportRequest channelReportRequest, HttpServletResponse response) {
        try {
            reportService.exportChannelReports(channelReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportChannelReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportChannelReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportChannelReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportChannelReport failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportAgentReport")
    public void exportAgentReport(
            @RequestBody @Valid AgentReportRequest agentReportRequest, HttpServletResponse response) {
        try {
            reportService.exportAgentReports(agentReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportAgentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgentReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgentReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgentReport failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportCurrencyReport")
    public void exportCurrencyReport(
            @RequestBody @Valid BaseReportRequest currencyReportRequest, HttpServletResponse response) {
        try {
            reportService.exportCurrencyReports(currencyReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportCurrencyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportCurrencyReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportCurrencyReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportCurrencyReport failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportPaymentReport")
    public void exportPaymentReport(
            @RequestBody @Valid PaymentReportRequest paymentReportRequest, HttpServletResponse response) {
        try {
            reportService.exportPaymentReports(paymentReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportPaymentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportPaymentReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportPaymentReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportPaymentReport failed, IOException message: " + e.getMessage());
        }
    }
}
