package com.pakgopay.controller;

import com.pakgopay.data.reqeust.bankCode.BankCodeQueryRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeUpdateRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.BankCodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank code management controller.
 * <p>
 * Note:
 * This file is intentionally added as a skeleton only.
 * API methods will be added in later steps.
 */
@RestController
@RequestMapping("/pakGoPay/server/bankCode")
@Slf4j
public class BankCodeController {

    @Autowired
    private BankCodeService bankCodeService;

    /**
     * Query bank code dictionary by bankName / bankCode / currencyCode.
     */
    @PostMapping("/queryBankCode")
    public CommonResponse queryBankCode(@RequestBody @Valid BankCodeQueryRequest request) {
        try {
            return bankCodeService.queryBankCode(request);
        } catch (Exception e) {
            log.error("queryBankCode failed, message={}", e.getMessage());
            return CommonResponse.fail(com.pakgopay.common.enums.ResultCode.FAIL,
                    "queryBankCode failed: " + e.getMessage());
        }
    }

    /**
     * Query full bank-code dictionary by currency and mark selection/status state for one payment channel.
     */
    @PostMapping("/queryPaymentBankCode")
    public CommonResponse queryPaymentBankCode(@RequestBody @Valid PaymentBankCodeQueryRequest request) {
        try {
            return bankCodeService.queryPaymentBankCode(request);
        } catch (Exception e) {
            log.error("queryPaymentBankCode failed, message={}", e.getMessage());
            return CommonResponse.fail(com.pakgopay.common.enums.ResultCode.FAIL,
                    "queryPaymentBankCode failed: " + e.getMessage());
        }
    }

    /**
     * Full-set update for payment-bank-code bindings by paymentId/currency.
     * <p>
     * Request items contain bankCode + supportType + status.
     * Relations missing from request will be removed from DB.
     */
    @PostMapping("/updatePaymentBankCodes")
    public CommonResponse updatePaymentBankCodes(@RequestBody @Valid PaymentBankCodeUpdateRequest request) {
        try {
            return bankCodeService.updatePaymentBankCodes(request);
        } catch (Exception e) {
            log.error("updatePaymentBankCodes failed, message={}", e.getMessage());
            return CommonResponse.fail(com.pakgopay.common.enums.ResultCode.FAIL,
                    "updatePaymentBankCodes failed: " + e.getMessage());
        }
    }
}
