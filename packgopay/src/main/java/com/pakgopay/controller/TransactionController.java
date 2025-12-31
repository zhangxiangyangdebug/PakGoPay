package com.pakgopay.controller;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.common.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.order.CollectionOrderService;
import com.pakgopay.service.order.PayOutOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/pakGoPay/server/v1")
public class TransactionController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;


    @PostMapping(value = "/createCollectionOrder")
    public WebAsyncTask<CommonResponse> createCollectionOrder(
            HttpServletRequest request, @Valid @RequestBody CollectionOrderRequest collectionOrderRequest) {
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return collectionOrderService.createCollectionOrder(collectionOrderRequest);
                    } catch (PakGoPayException e) {
                        return CommonResponse.fail(e.getCode(), "createPayOutOrder async timeout");
                    }
                });

        // task time out
        task.onTimeout(() -> CommonResponse.fail(ResultCode.REQUEST_TIME_OUT, "createCollectionOrder async timeout"));
        // task error
        task.onError(() -> CommonResponse.fail(ResultCode.FAIL, "createCollectionOrder async error"));

        return task;
    }

    @PostMapping(value = "/createPayOutOrder")
    public WebAsyncTask<CommonResponse> createPayOutOrder(
            HttpServletRequest request, @Valid @RequestBody PayOutOrderRequest payOutOrderRequest) {
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return payOutOrderService.createPayOutOrder(payOutOrderRequest);
                    } catch (PakGoPayException e) {
                        return CommonResponse.fail(e.getCode(), "createPayOutOrder async timeout");
                    }
                });
        // task time out
        task.onTimeout(() -> CommonResponse.fail(ResultCode.REQUEST_TIME_OUT, "createPayOutOrder async timeout"));
        // task error
        task.onError(() -> CommonResponse.fail(ResultCode.FAIL, "createPayOutOrder async error"));

        return task;
    }

    @GetMapping(value = "/queryOrder")
    public CommonResponse queryOrder(
            HttpServletRequest request, @RequestParam(value = "userId") String userId,
            @RequestParam(value = "merchantOrderNo") String merchantOrderNo) {

        if (!StringUtils.hasText(merchantOrderNo)) {
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "merchantOrderNo is empty");
        }

        try {
            if (merchantOrderNo.startsWith(CommonConstant.COLLECTION_PREFIX)) {
                return collectionOrderService.queryOrderInfo(userId, merchantOrderNo);
            }

            if (merchantOrderNo.startsWith(CommonConstant.PAYOUT_PREFIX)) {
                return payOutOrderService.queryOrderInfo(userId, merchantOrderNo);
            }
        } catch (PakGoPayException e) {
            return CommonResponse.fail(e.getCode(), "createPayOutOrder async timeout");
        }


        return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "merchantOrderNo is invalid");
    }
}
