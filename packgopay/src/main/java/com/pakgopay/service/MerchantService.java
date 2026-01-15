package com.pakgopay.service;

import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.validation.Valid;

public interface MerchantService {
    CommonResponse queryMerchant(@Valid MerchantQueryRequest merchantQueryRequest);
}
