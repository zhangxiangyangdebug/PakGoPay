package com.pakgopay.service;

import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;


public interface CurrencyTypeManagementService {

    CommonResponse getAllCurrencyType();

    CommonResponse getCurrencyById(Integer id);

    CommonResponse addNewCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);
}
