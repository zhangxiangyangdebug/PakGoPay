package com.pakgopay.controller;

import com.pakgopay.common.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.currencyTypeManagement.CurrencyTypeManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pakGoPay/server/CurrencyTypeManagement")
public class CurrencyManagementController {

    @Autowired
    private CurrencyTypeManagementService currencyTypeManagementService;

    @GetMapping("/currencyTypeInfo")
    public CommonResponse currencyTypeInfo() {
        return currencyTypeManagementService.getAllCurrencyType();
    }

    @PostMapping("/addCurrencyType")
    public CommonResponse addCurrencyType(@RequestBody CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        return currencyTypeManagementService.addNewCurrencyType(currencyTypeRequest, request);
    }

    @GetMapping("/getCurrencyById")
    public CommonResponse updateCurrencyType(Integer id) {
        return currencyTypeManagementService.getCurrencyById(id);
    }
}
