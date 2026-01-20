package com.pakgopay.service;

import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.account.AccountRechargeRequest;
import com.pakgopay.data.reqeust.merchant.MerchantAddRequest;
import com.pakgopay.data.reqeust.merchant.MerchantEditRequest;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;

public interface MerchantService {
    CommonResponse queryMerchant(MerchantQueryRequest merchantQueryRequest);

    CommonResponse editMerchant(MerchantEditRequest merchantEditRequest);

    CommonResponse addMerchant(MerchantAddRequest merchantAddRequest);

    CommonResponse queryMerchantAccount(AccountQueryRequest accountQueryRequest);

    void exportMerchantAccount(@Valid AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse editMerchantAccount(@Valid AccountEditRequest accountEditRequest);

    CommonResponse addMerchantAccount(@Valid AccountAddRequest accountAddRequest);

    CommonResponse queryMerchantRecharge(@Valid AccountQueryRequest accountQueryRequest);

    CommonResponse addMerchantRecharge(@Valid AccountRechargeRequest accountRechargeRequest);
}
