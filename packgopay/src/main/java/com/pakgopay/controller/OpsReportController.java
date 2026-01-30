package com.pakgopay.controller;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.OpsReportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server")
public class OpsReportController {

    @Autowired
    private OpsReportService opsReportService;

    @PostMapping(value = "queryOpsDailyReport")
    public CommonResponse queryOpsDailyReport(@Valid @RequestBody OpsReportRequest request) {
        try {
            return opsReportService.queryOpsDailyReports(request);
        } catch (PakGoPayException e) {
            log.error("queryOpsDailyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryOpsDailyReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryOpsMonthlyReport")
    public CommonResponse queryOpsMonthlyReport(@Valid @RequestBody OpsReportRequest request) {
        try {
            return opsReportService.queryOpsMonthlyReports(request);
        } catch (PakGoPayException e) {
            log.error("queryOpsMonthlyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryOpsMonthlyReport failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "queryOpsYearlyReport")
    public CommonResponse queryOpsYearlyReport(@Valid @RequestBody OpsReportRequest request) {
        try {
            return opsReportService.queryOpsYearlyReports(request);
        } catch (PakGoPayException e) {
            log.error("queryOpsYearlyReport failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryOpsYearlyReport failed: " + e.getMessage());
        }
    }
}
