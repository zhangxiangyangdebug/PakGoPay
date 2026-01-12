package com.pakgopay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.report.ReportService;
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
            writeJsonError(response, e.getCode(), "exportMerchantReport failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportMerchantReport failed, IOException message: {}", e.getMessage());
            writeJsonError(response,
                    ResultCode.FAIL, "exportMerchantReport failed, IOException message: " + e.getMessage());
        }
        log.info("exportMerchantReport end");
    }


    private void writeJsonError(HttpServletResponse response, ResultCode code, String msg) {
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");

            CommonResponse<Void> fail = CommonResponse.fail(code, msg);
            String json = new ObjectMapper().writeValueAsString(fail);
            response.getWriter().write(json);
        } catch (Exception ex) {
            log.error("writeJsonError failed", ex);
        }
    }

}
