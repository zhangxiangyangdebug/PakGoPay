package com.pakgopay.controller;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.report.ReportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class ReportController {


    @Autowired
    ReportService reportService;

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

}
