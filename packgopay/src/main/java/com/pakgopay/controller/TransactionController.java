package com.pakgopay.controller;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.PayOutOrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/v1")
public class TransactionController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;

    @PostMapping(value = "/queryCollectionOrders")
    public CommonResponse queryCollectionOrders(@RequestBody @Valid OrderQueryRequest request) {
        try {
            return collectionOrderService.queryCollectionOrders(request);
        } catch (PakGoPayException e) {
            log.error("queryCollectionOrders failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryCollectionOrders failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "/queryPayOutOrders")
    public CommonResponse queryPayOutOrders(@RequestBody @Valid OrderQueryRequest request) {
        try {
            return payOutOrderService.queryPayOutOrders(request);
        } catch (PakGoPayException e) {
            log.error("queryPayOutOrders failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryPayOutOrders failed: " + e.getMessage());
        }
    }
}
