package com.pakgopay.controller;

import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.account.WithdrawOrderAuditRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderCreateRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.WithdrawOrderService;
import com.pakgopay.service.common.OperateLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/withdrawOrder")
@RequiredArgsConstructor
public class WithdrawOrderController {

    private final WithdrawOrderService withdrawOrderService;
    private final OperateLogService operateLogService;

    @PostMapping("/create")
    public CommonResponse createWithdrawOrder(
            @RequestBody @Valid WithdrawOrderCreateRequest request) {
        try {
            CommonResponse response = withdrawOrderService.createWithdrawOrder(request);
            operateLogService.write(OperateInterfaceEnum.CREATE_WITHDRAW_ORDER,
                    request.getUserId(), request);
            return response;
        } catch (PakGoPayException e) {
            log.error("createWithdrawOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "createWithdrawOrder failed: " + e.getMessage());
        }
    }

    @PostMapping("/audit")
    public CommonResponse auditWithdrawOrder(
            @RequestBody @Valid WithdrawOrderAuditRequest request) {
        try {
            CommonResponse response = withdrawOrderService.auditWithdrawOrder(request);
            operateLogService.write(OperateInterfaceEnum.AUDIT_WITHDRAW_ORDER,
                    request.getWithdrawNo(), request);
            return response;
        } catch (PakGoPayException e) {
            log.error("auditWithdrawOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "auditWithdrawOrder failed: " + e.getMessage());
        }
    }

    @PostMapping("/page")
    public CommonResponse queryWithdrawOrderPage(
            @RequestBody @Valid WithdrawOrderQueryRequest request) {
        try {
            return withdrawOrderService.queryWithdrawOrderPage(request);
        } catch (PakGoPayException e) {
            log.error("queryWithdrawOrderPage failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryWithdrawOrderPage failed: " + e.getMessage());
        }
    }
}
