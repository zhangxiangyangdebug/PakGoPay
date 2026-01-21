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
    MerchantInfoDto getMerchantInfo(String userId) throws PakGoPayException;

    CommonResponse queryMerchant(MerchantQueryRequest merchantQueryRequest);

    CommonResponse editMerchant(MerchantEditRequest merchantEditRequest);

    CommonResponse addMerchant(MerchantAddRequest merchantAddRequest);

    CommonResponse queryMerchantAccount(AccountQueryRequest accountQueryRequest);

    void exportMerchantAccount(AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException;

    CommonResponse editMerchantAccount(AccountEditRequest accountEditRequest);

    CommonResponse addMerchantAccount(AccountAddRequest accountAddRequest);
}
