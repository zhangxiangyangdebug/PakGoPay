package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.account.*;
import com.pakgopay.data.reqeust.merchant.MerchantAddRequest;
import com.pakgopay.data.reqeust.merchant.MerchantEditRequest;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.reqeust.merchant.MerchantSecretKeyRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.MerchantService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.util.ExportFileUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/merchant")
public class MerchantStatementController {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private AccountStatementService accountStatementService;

    @PostMapping("/queryMerchant")
    public CommonResponse queryMerchant(@RequestBody @Valid MerchantQueryRequest merchantQueryRequest) {
        try {
            return merchantService.queryMerchants(merchantQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchant failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchant failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryMerchantSecretKey")
    public CommonResponse queryMerchantSecretKey(
            @RequestBody MerchantSecretKeyRequest request) {
        try {
            return merchantService.queryMerchantSecretKey(request, request.getUserId());
        } catch (PakGoPayException e) {
            log.error("queryMerchantSecretKey failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchantSecretKey failed: " + e.getMessage());
        }
    }

    @PostMapping("/resetMerchantSignKey")
    public CommonResponse resetMerchantSignKey(
            @RequestBody MerchantSecretKeyRequest request) {
        try {
            return merchantService.resetMerchantSignKey(request, request.getUserId());
        } catch (PakGoPayException e) {
            log.error("resetMerchantSignKey failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "resetMerchantSignKey failed: " + e.getMessage());
        }
    }

    @PostMapping("/editMerchant")
    public CommonResponse editMerchant(@RequestBody @Valid MerchantEditRequest merchantEditRequest) {
        try {
            return merchantService.updateMerchant(merchantEditRequest);
        } catch (PakGoPayException e) {
            log.error("editMerchant failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "editMerchant failed: " + e.getMessage());
        }
    }

    @PostMapping("/addMerchant")
    public CommonResponse addMerchant(@RequestBody @Valid MerchantAddRequest merchantAddRequest) {
        try {
            return merchantService.createMerchant(merchantAddRequest);
        } catch (PakGoPayException e) {
            log.error("addMerchant failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addMerchant failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryMerchantAccount")
    public CommonResponse queryMerchantAccount(
            @RequestBody @Valid AccountQueryRequest accountQueryRequest) {
        try {
            return merchantService.queryMerchantAccounts(accountQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryMerchantAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchantAccount failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "exportMerchantAccount")
    public void exportMerchantAccount(
            @RequestBody @Valid AccountQueryRequest accountQueryRequest, HttpServletResponse response) {
        try {
            merchantService.exportMerchantAccounts(accountQueryRequest, response);
        } catch (PakGoPayException e) {
            log.error("exportMerchantAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            ExportFileUtils.writeJsonError(response, e.getCode(), "exportMerchantAccount failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("exportMerchantAccount failed, IOException message: {}", e.getMessage());
            ExportFileUtils.writeJsonError(response,
                    ResultCode.FAIL, "exportMerchantAccount failed, IOException message: " + e.getMessage());
        }
    }

    @PostMapping("/editMerchantAccount")
    public CommonResponse editMerchantAccount(@RequestBody @Valid AccountEditRequest accountEditRequest, HttpServletRequest request) {
        try {
            return merchantService.updateMerchantAccount(accountEditRequest);
        } catch (PakGoPayException e) {
            log.error("editMerchantAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "editMerchantAccount failed: " + e.getMessage());
        }
    }

    @PostMapping("/addMerchantAccount")
    public CommonResponse addMerchantAccount(@RequestBody @Valid AccountAddRequest accountAddRequest, HttpServletRequest request) {
        try {
            return merchantService.createMerchantAccount(accountAddRequest);
        } catch (PakGoPayException e) {
            log.error("addMerchantAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addMerchantAccount failed: " + e.getMessage());
        }
    }

    @PostMapping("/queryAccountStatement")
    public CommonResponse queryAccountStatement(
            @RequestBody @Valid AccountStatementQueryRequest accountStatementQueryRequest) {
        try {
            return accountStatementService.queryAccountStatement(accountStatementQueryRequest);
        } catch (PakGoPayException e) {
            log.error("queryAccountStatement failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryAccountStatement failed: " + e.getMessage());
        }
    }

    @PostMapping("/createAccountStatement")
    public CommonResponse createAccountStatement(
            @RequestBody @Valid AccountStatementAddRequest accountStatementAddRequest) {
        try {
            return accountStatementService.createAccountStatement(accountStatementAddRequest);
        } catch (PakGoPayException e) {
            log.error("createAccountStatement failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "createAccountStatement failed: " + e.getMessage());
        }
    }

    @PostMapping("/editAccountStatement")
    public CommonResponse editAccountStatement(
            @RequestBody @Valid AccountStatementEditRequest accountStatementEditRequest) {
        try {
            return accountStatementService.updateAccountStatement(accountStatementEditRequest);
        } catch (PakGoPayException e) {
            log.error("editAccountStatement failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "editAccountStatement failed: " + e.getMessage());
        }
    }

}
