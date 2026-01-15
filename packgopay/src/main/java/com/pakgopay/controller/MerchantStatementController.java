package com.pakgopay.controller;

import com.google.gson.Gson;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.reqeust.merchant.MerchantStatementRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.merchant.MerchantStatementResponse;
import com.pakgopay.service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/merchant")
public class MerchantStatementController {

    @Autowired
    private MerchantService merchantService;

    @RequestMapping("/merchantStatement")
    public CommonResponse merchantStatementInfo(@RequestBody MerchantStatementRequest request) {
        System.out.println(request.toString());
        MerchantStatementResponse merchantStatementResponse = new MerchantStatementResponse();
        merchantStatementResponse.setOrderNO("DF0000000001");
        merchantStatementResponse.setMerchantName("代理商1");
        merchantStatementResponse.setTransactionType("现金交易");
        merchantStatementResponse.setTransactionStatus("交易成功");
        merchantStatementResponse.setTransactionCurrencyType("美金");
        merchantStatementResponse.setTransactionCommission("20");
        merchantStatementResponse.setBeforeTransactionAccountBalance("1000");
        merchantStatementResponse.setAfterTransactionAccountBalance("800");
        merchantStatementResponse.setTransactionCashAmount("200");
        merchantStatementResponse.setTransactionTime("2025-12-06");
        merchantStatementResponse.setTransactionReason("你管我");
        merchantStatementResponse.setOperator("admin");
        MerchantStatementResponse merchantStatementResponse2 = new MerchantStatementResponse();
        merchantStatementResponse2.setOrderNO("DF0000000002");
        merchantStatementResponse2.setMerchantName("代理商1");
        merchantStatementResponse2.setTransactionType("现金交易");
        merchantStatementResponse2.setTransactionStatus("交易成功");
        merchantStatementResponse2.setTransactionCurrencyType("美金");
        merchantStatementResponse2.setTransactionCommission("20");
        merchantStatementResponse2.setBeforeTransactionAccountBalance("1000");
        merchantStatementResponse2.setAfterTransactionAccountBalance("800");
        merchantStatementResponse2.setTransactionCashAmount("200");
        merchantStatementResponse2.setTransactionTime("2025-12-06");
        merchantStatementResponse2.setTransactionReason("你管我");
        merchantStatementResponse2.setOperator("admin");
        List<MerchantStatementResponse> merchantStatementResponseList = new ArrayList<>();
        merchantStatementResponseList.add(merchantStatementResponse);
        merchantStatementResponseList.add(merchantStatementResponse2);
        return CommonResponse.success(new Gson().toJson(merchantStatementResponseList));
    }

    @PostMapping("/queryMerchant")
    public CommonResponse queryMerchant(@RequestBody @Valid MerchantQueryRequest merchantQueryRequest, HttpServletRequest request) {
        log.info("queryMerchant start");
        try {
            return merchantService.queryMerchant(merchantQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchant failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchant failed: " + e.getMessage());
        }
    }

}
