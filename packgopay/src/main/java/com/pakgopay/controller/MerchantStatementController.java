package com.pakgopay.controller;

import com.google.gson.Gson;
import com.pakgopay.common.reqeust.MerchantStatementRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.MerchantStatementResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pakGoPay/server/merchant")
public class MerchantStatementController {

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

}
