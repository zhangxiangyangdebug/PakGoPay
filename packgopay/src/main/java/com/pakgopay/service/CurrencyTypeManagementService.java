package com.pakgopay.service;

import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;


public interface CurrencyTypeManagementService {

    CommonResponse listCurrencyTypes();

    CommonResponse fetchCurrencyById(Integer id);

    CommonResponse createCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);
}
