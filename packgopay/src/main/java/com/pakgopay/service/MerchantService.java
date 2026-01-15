package com.pakgopay.service;

import com.pakgopay.data.reqeust.merchant.MerchantAddRequest;
import com.pakgopay.data.reqeust.merchant.MerchantEditRequest;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface MerchantService {
    CommonResponse queryMerchant(MerchantQueryRequest merchantQueryRequest);

    CommonResponse editMerchant(MerchantEditRequest merchantEditRequest);

    CommonResponse addMerchant(MerchantAddRequest merchantAddRequest);
}
