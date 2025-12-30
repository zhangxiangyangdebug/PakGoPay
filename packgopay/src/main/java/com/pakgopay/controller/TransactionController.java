package com.pakgopay.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/pakGoPay/server/v1")
public class TransactionController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;


    @PostMapping(value = "/createCollectionOrder")
    public WebAsyncTask<CommonResponse> createCollectionOrder(HttpServletRequest request, @Valid @RequestBody CollectionOrderRequest collectionOrderRequest) throws PakGoPayException {
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> collectionOrderService.createCollectionOrder(collectionOrderRequest));

        task.onTimeout(() -> {
            throw new PakGoPayException(ResultCode.REQUEST_TIME_OUT, "createCollectionOrder async timeout");
        });

        task.onError(() -> {
            throw new PakGoPayException(ResultCode.FAIL, "createCollectionOrder async error");
        });

        return task;
    }

    @PostMapping(value = "/createPayOutOrder")
    public WebAsyncTask<CommonResponse> createPayOutOrder(HttpServletRequest request, @Valid @RequestBody PayOutOrderRequest payOutOrderRequest) {
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> payOutOrderService.createPayOutOrder(payOutOrderRequest));
        ;

        task.onTimeout(() -> {
            throw new PakGoPayException(ResultCode.REQUEST_TIME_OUT, "createPayOutOrder async timeout");
        });

        task.onError(() -> {
            throw new PakGoPayException(ResultCode.FAIL, "createPayOutOrder async error");
        });

        return task;
    }
}
