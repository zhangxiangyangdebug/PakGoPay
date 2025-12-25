package com.pakgopay.controller;

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

@RestController
@RequestMapping("/pakGoPay/server/v1")
public class TransactionController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;


    @PostMapping(value = "/createCollectionOrder")
    public CommonResponse createCollectionOrder(HttpServletRequest request, @Valid @RequestBody CollectionOrderRequest collectionOrderRequest) {
        CommonResponse commonResponse = collectionOrderService.createCollectionOrder(collectionOrderRequest);
        return commonResponse;
    }

    @PostMapping(value = "/createPayOutOrder")
    public CommonResponse createPayOutOrder(HttpServletRequest request, @Valid @RequestBody PayOutOrderRequest payOutOrderRequest) {
        CommonResponse commonResponse = payOutOrderService.createPayOutOrder(payOutOrderRequest);
        return commonResponse;
    }
}
