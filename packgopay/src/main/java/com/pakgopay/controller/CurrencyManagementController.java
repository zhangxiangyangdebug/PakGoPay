package com.pakgopay.controller;

import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.CurrencyTypeManagementService;
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
        return currencyTypeManagementService.listCurrencyTypes();
    }

    @PostMapping("/addCurrencyType")
    public CommonResponse addCurrencyType(@RequestBody CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        return currencyTypeManagementService.createCurrencyType(currencyTypeRequest, request);
    }

    @GetMapping("/getCurrencyById")
    public CommonResponse updateCurrencyType(Integer id) {
        return currencyTypeManagementService.fetchCurrencyById(id);
    }
}
