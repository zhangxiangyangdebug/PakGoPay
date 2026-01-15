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
        log.info("queryMerchantReport start");
        try {
            return reportService.queryMerchantReport(merchantReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchantReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchantReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryChannelReport")
    public CommonResponse queryChannelReport(@RequestBody @Valid ChannelReportRequest channelReportRequest) {
        log.info("queryChannelReport start");
        try {
            return reportService.queryChannelReport(channelReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryChannelReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryChannelReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryAgentReport")
    public CommonResponse queryAgentReport(@RequestBody @Valid AgentReportRequest agentReportRequest) {
        log.info("queryAgentReport start");
        try {
            return reportService.queryAgentReport(agentReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryAgentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryAgentReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryCurrencyReport")
    public CommonResponse queryCurrencyReport(@RequestBody @Valid BaseReportRequest currencyReportRequest) {
        log.info("queryCurrencyReport start");
        try {
            return reportService.queryCurrencyReport(currencyReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryCurrencyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryCurrencyReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryPaymentReport")
    public CommonResponse queryPaymentReport(@RequestBody @Valid PaymentReportRequest paymentReportRequest) {
        log.info("queryPaymentReport start");
        try {
            return reportService.queryPaymentReport(paymentReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryPaymentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryPaymentReport failed: " + e.getMessage());
        }
    }

    //-------------------------------- export data ---------------------------------------------------------------------
    @PostMapping(value = "exportMerchantReport")
    public void exportMerchantReport(
            @RequestBody @Valid MerchantReportRequest merchantReportRequest, HttpServletResponse response) {
        log.info("exportMerchantReport start");
        try {
            reportService.exportMerchantReport(merchantReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportMerchantReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportMerchantReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportMerchantReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportMerchantReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportMerchantReport end");
    }

    @PostMapping(value = "exportChannelReport")
    public void exportChannelReport(
            @RequestBody @Valid ChannelReportRequest channelReportRequest, HttpServletResponse response) {
        log.info("exportChannelReport start");
        try {
            reportService.exportChannelReport(channelReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportChannelReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportChannelReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportChannelReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportChannelReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportChannelReport end");
    }

    @PostMapping(value = "exportAgentReport")
    public void exportAgentReport(
            @RequestBody @Valid AgentReportRequest agentReportRequest, HttpServletResponse response) {
        log.info("exportAgentReport start");
        try {
            reportService.exportAgentReport(agentReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportAgentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportAgentReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportAgentReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportAgentReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportAgentReport end");
    }

    @PostMapping(value = "exportCurrencyReport")
    public void exportCurrencyReport(
            @RequestBody @Valid BaseReportRequest currencyReportRequest, HttpServletResponse response) {
        log.info("exportCurrencyReport start");
        try {
            reportService.exportCurrencyReport(currencyReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportCurrencyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportCurrencyReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportCurrencyReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportCurrencyReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportCurrencyReport end");
    }

    @PostMapping(value = "exportPaymentReport")
    public void exportPaymentReport(
            @RequestBody @Valid PaymentReportRequest paymentReportRequest, HttpServletResponse response) {
        log.info("exportPaymentReport start");
        try {
            reportService.exportPaymentReport(paymentReportRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportPaymentReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportPaymentReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportPaymentReport failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportPaymentReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportPaymentReport end");
    }
}
