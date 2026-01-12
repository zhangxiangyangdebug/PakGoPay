package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.channel.ChannelRequest;
import com.pakgopay.common.reqeust.channel.PaymentRequest;
import com.pakgopay.common.reqeust.report.PaymentReportRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.channel.impl.ChannelPaymentServiceImpl;
import com.pakgopay.util.ExportFileUtils;
import jakarta.servlet.http.HttpServletRequest;
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
public class ChannelPaymentController {

    @Autowired
    private ChannelPaymentServiceImpl channelPaymentService;

    @PostMapping("/queryChannel")
    public CommonResponse queryChannel(@RequestBody @Valid ChannelRequest channelRequest, HttpServletRequest request) {
        log.info("queryChannel start");
        try {
            return channelPaymentService.queryChannel(channelRequest);
        } catch (PakGoPayException e) {
            log.error("queryChannel failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryChannel failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryPayment")
    public CommonResponse queryPayment(@RequestBody @Valid PaymentRequest paymentRequest, HttpServletRequest request) {
        log.info("queryPayment start");
        try {
            return channelPaymentService.queryPayment(paymentRequest);
        } catch (PakGoPayException e) {
            log.error("queryPayment failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryPayment failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportChannel")
    public void exportChannel(
            @RequestBody @Valid ChannelRequest channelRequest, HttpServletResponse response) {
        log.info("exportChannel start");
        try {
            channelPaymentService.exportChannel(channelRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportChannel failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportChannel failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportChannel failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportChannel failed, IOException message: " + e.getMessage());
        }
        log.info("exportChannel end");
    }

    @PostMapping(value = "exportPayment")
    public void exportPayment(
            @RequestBody @Valid PaymentRequest paymentRequest, HttpServletResponse response) {
        log.info("exportPayment start");
        try {
            channelPaymentService.exportPayment(paymentRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportPayment failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportPayment failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportPayment failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportPayment failed, IOException message: " + e.getMessage());
        }
        log.info("exportPayment end");
    }
}
