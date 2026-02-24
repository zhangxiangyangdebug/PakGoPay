package com.pakgopay.service;

import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;


public interface CurrencyTypeManagementService {

    CommonResponse listCurrencyTypes(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request);

    CommonResponse createCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);

    CommonResponse updateCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);

}
