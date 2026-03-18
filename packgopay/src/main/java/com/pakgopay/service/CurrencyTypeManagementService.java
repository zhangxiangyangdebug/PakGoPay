package com.pakgopay.service;

import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.dto.CurrencyTypeSyncExcelRow;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;


public interface CurrencyTypeManagementService {

    CommonResponse listCurrencyTypes(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request);

    CommonResponse createCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);

    CommonResponse updateCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);

    CommonResponse syncData(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest httpServletRequest);

    CommonResponse syncCurrencyTypesFromRows(
            List<CurrencyTypeSyncExcelRow> rows,
            String source,
            String userId,
            String userName);

}
