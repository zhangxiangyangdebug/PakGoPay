package com.pakgopay.controller;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.reqeust.transaction.QueryBalanceApiRequest;
import com.pakgopay.data.reqeust.transaction.QueryOrderApiRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.transaction.CollectionOrderService;
import com.pakgopay.service.transaction.PayOutOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/api/server/v1")
public class TransactionApiController {

    @Autowired
    private CollectionOrderService collectionOrderService;

    @Autowired
    private PayOutOrderService payOutOrderService;

    @PostMapping(value = "/createCollectionOrder")
    public WebAsyncTask<CommonResponse> createCollectionOrder(
            HttpServletRequest request,
            @RequestHeader(value = "Authorization") String authorization,
            @Valid @RequestBody CollectionOrderRequest collectionOrderRequest) {
        log.info("createCollectionOrder request, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}",
                collectionOrderRequest.getMerchantId(),
                collectionOrderRequest.getMerchantOrderNo(),
                collectionOrderRequest.getCurrency(),
                collectionOrderRequest.getAmount(),
                collectionOrderRequest.getPaymentNo());
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return collectionOrderService.createCollectionOrder(collectionOrderRequest, authorization);
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
            HttpServletRequest request,
            @RequestHeader(value = "Authorization") String authorization,
            @Valid @RequestBody PayOutOrderRequest payOutOrderRequest) {
        log.info("createPayOutOrder request, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}",
                payOutOrderRequest.getMerchantId(),
                payOutOrderRequest.getMerchantOrderNo(),
                payOutOrderRequest.getCurrency(),
                payOutOrderRequest.getAmount(),
                payOutOrderRequest.getPaymentNo());
        WebAsyncTask<CommonResponse> task = new WebAsyncTask<>(300000L,
                () -> {
                    try {
                        return payOutOrderService.createPayOutOrder(payOutOrderRequest, authorization);
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

    @PostMapping(value = "/queryOrder")
    public CommonResponse queryOrder(
            HttpServletRequest request,
            @RequestHeader(value = "Authorization") String authorization,
            @Valid @RequestBody QueryOrderApiRequest queryRequest) {
        String orderType = queryRequest.getOrderType();

        try {
            if (CommonConstant.COLLECTION_PREFIX.equals(orderType)) {
                return collectionOrderService.queryOrderInfo(queryRequest, authorization);
            }

            if (CommonConstant.PAYOUT_PREFIX.equals(orderType)) {
                return payOutOrderService.queryOrderInfo(queryRequest, authorization);
            }
        } catch (PakGoPayException e) {
            log.error("queryOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryOrder failed, " + e.getMessage());
        }

        log.warn("orderType is invalid, orderType={}", orderType);
        return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "orderType is invalid");
    }

    @PostMapping(value = "/balance")
    public CommonResponse queryBalance(
            HttpServletRequest request,
            @RequestHeader(value = "Authorization") String authorization,
            @Valid @RequestBody QueryBalanceApiRequest queryRequest) {
        try {
            return collectionOrderService.queryBalance(queryRequest, authorization);
        } catch (PakGoPayException e) {
            log.error("fetchMerchantAvailableBalance failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryBalance failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "/notifyTransaction")
    public Object handleNotify(@RequestBody Map<String, Object> notifyData) {
        log.info("notify received, notifyData={}", notifyData);
        String orderNo = extractOrderNo(notifyData);
        Map<String, Object> data = getDataMap(notifyData);
        log.info("notify parsed, order_no={}", orderNo);
        if (orderNo != null && orderNo.startsWith(CommonConstant.COLLECTION_PREFIX)) {
            return collectionOrderService.handleNotify(data);
        }
        if (orderNo != null && orderNo.startsWith(CommonConstant.PAYOUT_PREFIX)) {
            return payOutOrderService.handleNotify(data);
        }
        return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, "order_no is valid").toString();
    }

    private String extractOrderNo(Map<String, Object> payload) {
        return extractDataField(payload, "order_no");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDataMap(Map<String, Object> notifyData) {
        Object dataObj = notifyData.get("data");
        return dataObj instanceof Map<?, ?> ? (Map<String, Object>) dataObj : null;
    }

    private String extractDataField(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object raw = dataMap.get(key);
            if (raw != null) {
                return String.valueOf(raw);
            }
        }
        return null;
    }
}
