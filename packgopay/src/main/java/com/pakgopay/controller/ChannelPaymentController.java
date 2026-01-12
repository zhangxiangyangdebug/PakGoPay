package com.pakgopay.controller;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.channel.ChannelRequest;
import com.pakgopay.common.reqeust.channel.PaymentRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.channel.impl.ChannelPaymentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
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
}
