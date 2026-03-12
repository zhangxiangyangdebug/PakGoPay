package com.pakgopay.controller;

import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.transaction.CollectionOrderRequest;
import com.pakgopay.data.reqeust.transaction.MerchantAvailableChannelRequest;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.reqeust.transaction.OrderFlowLogQueryRequest;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.reqeust.transaction.OrderReverseRequest;
import com.pakgopay.data.reqeust.transaction.PayOutOrderRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.common.OrderFlowLogService;
import com.pakgopay.service.common.OperateLogService;
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

    @Autowired
    private ChannelPaymentService channelPaymentService;

    @Autowired
    private OperateLogService operateLogService;

    @Autowired
    private OrderFlowLogService orderFlowLogService;

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

    /**
     * Manually create collection order without apiKey/sign verification.
     */
    @PostMapping(value = "/manualCreateCollectionOrder")
    public CommonResponse manualCreateCollectionOrder(@RequestBody @Valid CollectionOrderRequest request) {
        log.info("manualCreateCollectionOrder request, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}, manualOrderType={}",
                request.getMerchantId(),
                request.getMerchantOrderNo(),
                request.getCurrency(),
                request.getAmount(),
                request.getPaymentNo(),
                request.getManualOrderType());
        try {
            CommonResponse response = collectionOrderService.manualCreateCollectionOrder(request);
            operateLogService.write(OperateInterfaceEnum.MANUAL_CREATE_COLLECTION_ORDER, request.getUserId(), request);
            log.info("manualCreateCollectionOrder success, merchantId={}, merchantOrderNo={}",
                    request.getMerchantId(), request.getMerchantOrderNo());
            return response;
        } catch (PakGoPayException e) {
            log.error("manualCreateCollectionOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "manualCreateCollectionOrder failed: " + e.getMessage());
        }
    }

    /**
     * Manually create payout order without apiKey/sign verification.
     */
    @PostMapping(value = "/manualCreatePayOutOrder")
    public CommonResponse manualCreatePayOutOrder(@RequestBody @Valid PayOutOrderRequest request) {
        log.info("manualCreatePayOutOrder request, merchantId={}, merchantOrderNo={}, currency={}, amount={}, paymentNo={}, manualOrderType={}",
                request.getMerchantId(),
                request.getMerchantOrderNo(),
                request.getCurrency(),
                request.getAmount(),
                request.getPaymentNo(),
                request.getManualOrderType());
        try {
            CommonResponse response = payOutOrderService.manualCreatePayOutOrder(request);
            operateLogService.write(OperateInterfaceEnum.MANUAL_CREATE_PAYOUT_ORDER, request.getUserId(), request);
            log.info("manualCreatePayOutOrder success, merchantId={}, merchantOrderNo={}",
                    request.getMerchantId(), request.getMerchantOrderNo());
            return response;
        } catch (PakGoPayException e) {
            log.error("manualCreatePayOutOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "manualCreatePayOutOrder failed: " + e.getMessage());
        }
    }

    /**
     * Manually trigger collection-order notify flow by normalized notify payload.
     */
    @PostMapping(value = "/manualNotifyCollectionOrder")
    public CommonResponse manualNotifyCollectionOrder(@RequestBody @Valid NotifyRequest request) {
        log.info("manualNotifyCollectionOrder request, transactionNo={}, merchantNo={}, status={}, remark={}",
                request.getTransactionNo(), request.getMerchantNo(), request.getStatus(), request.getRemark());
        try {
            CommonResponse response = collectionOrderService.manualHandleNotify(request);
            operateLogService.write(OperateInterfaceEnum.MANUAL_NOTIFY_COLLECTION_ORDER, request.getUserId(), request);
            log.info("manualNotifyCollectionOrder success, transactionNo={}", request.getTransactionNo());
            return response;
        } catch (PakGoPayException e) {
            log.error("manualNotifyCollectionOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "manualNotifyCollectionOrder failed: " + e.getMessage());
        }
    }

    /**
     * Manually trigger payout-order notify flow by normalized notify payload.
     */
    @PostMapping(value = "/manualNotifyPayOutOrder")
    public CommonResponse manualNotifyPayOutOrder(@RequestBody @Valid NotifyRequest request) {
        log.info("manualNotifyPayOutOrder request, transactionNo={}, merchantNo={}, status={}, remark={}",
                request.getTransactionNo(), request.getMerchantNo(), request.getStatus(), request.getRemark());
        try {
            CommonResponse response = payOutOrderService.manualHandleNotify(request);
            operateLogService.write(OperateInterfaceEnum.MANUAL_NOTIFY_PAYOUT_ORDER, request.getUserId(), request);
            log.info("manualNotifyPayOutOrder success, transactionNo={}", request.getTransactionNo());
            return response;
        } catch (PakGoPayException e) {
            log.error("manualNotifyPayOutOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "manualNotifyPayOutOrder failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "/queryMerchantAvailableChannels")
    public CommonResponse queryMerchantAvailableChannels(
            @RequestBody @Valid MerchantAvailableChannelRequest request) {
        // Query merchant-available payment channels by merchantId.
        log.info("queryMerchantAvailableChannels request, merchantId={}", request.getMerchantId());
        try {
            return channelPaymentService.queryMerchantAvailableChannels(request.getMerchantId());
        } catch (PakGoPayException e) {
            log.error("queryMerchantAvailableChannels failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "queryMerchantAvailableChannels failed: " + e.getMessage());
        }
    }

    /**
     * Manually reverse a SUCCESS order:
     * 0 = collection, 1 = payout.
     */
    @PostMapping(value = "/manualReverseOrder")
    public CommonResponse manualReverseOrder(@RequestBody @Valid OrderReverseRequest request) {
        log.info("manualReverseOrder request, transactionNo={}, bizType={}, operatorUserId={}",
                request.getTransactionNo(), request.getBizType(), request.getUserId());
        try {
            if (request.getBizType() != null && request.getBizType() == 0) {
                CommonResponse response = collectionOrderService.reverseOrder(request);
                operateLogService.write(OperateInterfaceEnum.MANUAL_REVERSE_COLLECTION_ORDER, request.getUserId(), request);
                return response;
            }
            CommonResponse response = payOutOrderService.reverseOrder(request);
            operateLogService.write(OperateInterfaceEnum.MANUAL_REVERSE_PAYOUT_ORDER, request.getUserId(), request);
            return response;
        } catch (PakGoPayException e) {
            log.error("manualReverseOrder failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "manualReverseOrder failed: " + e.getMessage());
        }
    }

    /**
     * Query order flow logs by transactionNo.
     */
    @PostMapping(value = "/queryOrderFlowLogs")
    public CommonResponse queryOrderFlowLogs(@RequestBody @Valid OrderFlowLogQueryRequest request) {
        log.info("queryOrderFlowLogs request, transactionNo={}", request.getTransactionNo());
        try {
            return CommonResponse.success(orderFlowLogService.listByTransactionNo(request.getTransactionNo()));
        } catch (Exception e) {
            log.error("queryOrderFlowLogs failed, transactionNo={}, message={}",
                    request.getTransactionNo(), e.getMessage());
            return CommonResponse.fail(ResultCode.FAIL, "queryOrderFlowLogs failed: " + e.getMessage());
        }
    }
}
