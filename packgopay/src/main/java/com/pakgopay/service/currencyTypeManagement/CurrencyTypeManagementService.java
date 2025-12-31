package com.pakgopay.service.currencyTypeManagement;

import com.pakgopay.common.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.common.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;


public interface CurrencyTypeManagementService {

    CommonResponse getAllCurrencyType();

    CommonResponse getCurrencyById(Integer id);

    CommonResponse addNewCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);
}
