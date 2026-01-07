package com.pakgopay.controller;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.MerchantReportRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.report.ReportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class ReportController {


    @Autowired
    ReportService reportService;

    @GetMapping(value = "queryMerchantReport")
    public CommonResponse queryMerchantReport(@RequestBody @Valid MerchantReportRequest merchantReportRequest) {
        log.info("queryMerchantReport start");
        try {
            return reportService.queryMerchantReport(merchantReportRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchantAvailableBalance failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryBalance failed: " + e.getMessage());
        }
    }


}
