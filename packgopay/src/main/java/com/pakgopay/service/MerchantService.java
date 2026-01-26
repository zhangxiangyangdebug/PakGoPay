package com.pakgopay.service;

import com.pakgopay.data.reqeust.account.*;
import com.pakgopay.data.reqeust.merchant.MerchantAddRequest;
import com.pakgopay.data.reqeust.merchant.MerchantEditRequest;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface MerchantService {
    MerchantInfoDto fetchMerchantInfo(String userId) throws PakGoPayException;

    CommonResponse queryMerchants(MerchantQueryRequest merchantQueryRequest);

    CommonResponse updateMerchant(MerchantEditRequest merchantEditRequest);

    CommonResponse createMerchant(MerchantAddRequest merchantAddRequest);

    CommonResponse queryMerchantAccounts(AccountQueryRequest accountQueryRequest);

    void exportMerchantAccounts(AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse updateMerchantAccount(AccountEditRequest accountEditRequest);

    CommonResponse createMerchantAccount(AccountAddRequest accountAddRequest);
}
