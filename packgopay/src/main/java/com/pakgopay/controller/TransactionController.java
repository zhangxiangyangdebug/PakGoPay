package com.pakgopay.controller;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.PayOutOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/v1")
public class TransactionController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;

    @Autowired
    private BalanceService balanceService;


    @PostMapping(value = "/createCollectionOrder")
    public WebAsyncTask<CommonResponse> createCollectionOrder(
            HttpServletRequest request, @Valid @RequestBody CollectionOrderRequest collectionOrderRequest) {
        log.info("createCollectionOrder start");
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return collectionOrderService.createCollectionOrder(collectionOrderRequest);
                    } catch (PakGoPayException e) {
                        log.error("createCollectionOrder error, code: {} message: {}",e.getErrorCode(), e.getMessage());
                        return CommonResponse.fail(e.getCode(), e.getMessage());
                    }
                });

        // task time out
        task.onTimeout(() -> {
            log.error("createCollectionOrder async timeout");
            return CommonResponse.fail(ResultCode.REQUEST_TIME_OUT, "createCollectionOrder async timeout");
        });
        // task error
        task.onError(() -> {
            log.error("createCollectionOrder async error");
            return CommonResponse.fail(ResultCode.FAIL, "createCollectionOrder async error");
        });

        return task;
    }

    @PostMapping(value = "/createPayOutOrder")
    public WebAsyncTask<CommonResponse> createPayOutOrder(
            HttpServletRequest request, @Valid @RequestBody PayOutOrderRequest payOutOrderRequest) {
        log.info("createPayOutOrder start");
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return payOutOrderService.createPayOutOrder(payOutOrderRequest);
                    } catch (PakGoPayException e) {
                        log.error("createPayOutOrder error, code {} message {}",e.getErrorCode(), e.getMessage());
                        return CommonResponse.fail(e.getCode(), e.getMessage());
                    }
                });
        // task time out
        task.onTimeout(() -> {
            log.error("createPayOutOrder async timeout");
            return CommonResponse.fail(ResultCode.REQUEST_TIME_OUT, "createPayOutOrder async timeout");
        });
        // task error
        task.onError(() -> {
            log.error("createPayOutOrder async error");
            return CommonResponse.fail(ResultCode.FAIL, "createPayOutOrder async error");
        });

        return task;
    }

    @GetMapping(value = "/queryOrder")
    public CommonResponse queryOrder(
            HttpServletRequest request, @RequestParam(value = "userId") String userId,
            @RequestParam(value = "transactionNo") String transactionNo) {
        log.info("queryOrder start");
        if (!StringUtils.hasText(transactionNo)) {
            log.error("transactionNo is empty");
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "transactionNo is empty");
        }

        try {
            if (transactionNo.startsWith(CommonConstant.COLLECTION_PREFIX)) {
                return collectionOrderService.queryOrderInfo(userId, transactionNo);
            }

            if (transactionNo.startsWith(CommonConstant.PAYOUT_PREFIX)) {
                return payOutOrderService.queryOrderInfo(userId, transactionNo);
            }
        } catch (PakGoPayException e) {
            log.error("queryOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryOrder failed, " + e.getMessage());
        }

        log.info("transactionNo is invalid, transactionNo {}", transactionNo);
        return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "transactionNo is invalid");
    }

    @GetMapping(value = "/balance")
    public CommonResponse queryBalance(
            HttpServletRequest request, @RequestParam(value = "userId") String userId) {
        log.info("queryBalance start");
        if (!StringUtils.hasText(userId)) {
            log.error("userId is empty");
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "userId is empty");
        }

        try {
            return balanceService.queryMerchantAvailableBalance(userId);
        } catch (PakGoPayException e) {
            log.error("queryMerchantAvailableBalance failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryBalance failed: " + e.getMessage());
        }
    }
}
