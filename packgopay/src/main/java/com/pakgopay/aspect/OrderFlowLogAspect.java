package com.pakgopay.aspect;

import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.transaction.OrderHandler;
import com.pakgopay.service.common.OrderFlowLogService;
import com.pakgopay.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class OrderFlowLogAspect {

    @Autowired
    private OrderFlowLogService orderFlowLogService;

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.handleCol(..))")
    public Object aroundHandleCol(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        CollectionCreateEntity request = args != null && args.length > 0 && args[0] instanceof CollectionCreateEntity
                ? (CollectionCreateEntity) args[0] : null;
        String transactionNo = request == null ? null : request.getTransactionNo();
        logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_CREATE_REQUEST, true, request);
        try {
            Object result = pjp.proceed();
            logByTransactionNo(
                    transactionNo,
                    OrderFlowStepEnum.THIRD_CREATE_RESPONSE,
                    resolveThirdCreateSuccess(result),
                    result);
            return result;
        } catch (Throwable e) {
            logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_CREATE_RESPONSE, false, buildErrorPayload(e));
            throw e;
        }
    }

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.handlePay(..))")
    public Object aroundHandlePay(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        PayCreateEntity request = args != null && args.length > 0 && args[0] instanceof PayCreateEntity
                ? (PayCreateEntity) args[0] : null;
        String transactionNo = request == null ? null : request.getTransactionNo();
        logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_CREATE_REQUEST, true, request);
        try {
            Object result = pjp.proceed();
            logByTransactionNo(
                    transactionNo,
                    OrderFlowStepEnum.THIRD_CREATE_RESPONSE,
                    resolveThirdCreateSuccess(result),
                    result);
            return result;
        } catch (Throwable e) {
            logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_CREATE_RESPONSE, false, buildErrorPayload(e));
            throw e;
        }
    }

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.handleNotify(..))")
    public Object aroundHandleNotify(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = args != null && args.length > 0 && args[0] instanceof Map<?, ?>
                ? (Map<String, Object>) args[0] : null;
        String transactionNo = extractTransactionNo(body);
        logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_NOTIFY_REQUEST, true, body);
        return pjp.proceed();
    }

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.handleCollectionQuery(..))")
    public Object aroundHandleCollectionQuery(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        CollectionQueryEntity request = args != null && args.length > 0 && args[0] instanceof CollectionQueryEntity
                ? (CollectionQueryEntity) args[0] : null;
        String transactionNo = request == null ? null : request.getTransactionNo();
        logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_QUERY_REQUEST, true, request);
        try {
            Object result = pjp.proceed();
            logByTransactionNo(
                    transactionNo,
                    OrderFlowStepEnum.THIRD_QUERY_RESPONSE,
                    resolveThirdQuerySuccess(result),
                    result);
            return result;
        } catch (Throwable e) {
            logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_QUERY_RESPONSE, false, buildErrorPayload(e));
            throw e;
        }
    }

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.handlePayQuery(..))")
    public Object aroundHandlePayQuery(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        PayQueryEntity request = args != null && args.length > 0 && args[0] instanceof PayQueryEntity
                ? (PayQueryEntity) args[0] : null;
        String transactionNo = request == null ? null : request.getTransactionNo();
        logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_QUERY_REQUEST, true, request);
        try {
            Object result = pjp.proceed();
            logByTransactionNo(
                    transactionNo,
                    OrderFlowStepEnum.THIRD_QUERY_RESPONSE,
                    resolveThirdQuerySuccess(result),
                    result);
            return result;
        } catch (Throwable e) {
            logByTransactionNo(transactionNo, OrderFlowStepEnum.THIRD_QUERY_RESPONSE, false, buildErrorPayload(e));
            throw e;
        }
    }

    @Around("execution(* com.pakgopay.service.transaction.OrderHandler.sendNotifyToMerchant(..))")
    public Object aroundSendNotifyToMerchant(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = args != null && args.length > 0 && args[0] instanceof Map<?, ?>
                ? (Map<String, Object>) args[0] : null;
        String transactionNo = extractTransactionNo(body);
        logByTransactionNo(transactionNo, OrderFlowStepEnum.MERCHANT_NOTIFY_REQUEST, true, body);
        try {
            Object result = pjp.proceed();
            logByTransactionNo(
                    transactionNo,
                    OrderFlowStepEnum.MERCHANT_NOTIFY_RESPONSE,
                    resolveMerchantNotifySuccess(result),
                    result);
            return result;
        } catch (Throwable e) {
            logByTransactionNo(transactionNo, OrderFlowStepEnum.MERCHANT_NOTIFY_RESPONSE, false, buildErrorPayload(e));
            throw e;
        }
    }

    private void logByTransactionNo(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        if (transactionNo == null || transactionNo.isBlank() || step == null) {
            return;
        }
        if (CommonUtil.isCollectionTransactionNo(transactionNo)) {
            orderFlowLogService.logCollection(transactionNo, step, success, payload);
        } else if (CommonUtil.isPayoutTransactionNo(transactionNo)) {
            orderFlowLogService.logPayout(transactionNo, step, success, payload);
        }
    }

    private String extractTransactionNo(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object value = body.get("transactionNo");
        if (value == null) {
            value = body.get("transaction_no");
        }
        if (value == null) {
            value = body.get("order_no");
        }
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> buildErrorPayload(Throwable e) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("error", e == null ? null : e.getMessage());
        payload.put("type", e == null ? null : e.getClass().getName());
        return payload;
    }

    private boolean resolveMerchantNotifySuccess(Object result) {
        if (result instanceof OrderHandler.NotifyResult notifyResult) {
            return notifyResult.isSuccess();
        }
        return true;
    }

    private boolean resolveThirdCreateSuccess(Object result) {
        if (result instanceof PaymentHttpResponse response) {
            Integer code = response.getCode();
            // Internal normalized success code is 0; some handlers may still return 200.
            return Integer.valueOf(0).equals(code) || Integer.valueOf(200).equals(code);
        }
        return result != null;
    }

    private boolean resolveThirdQuerySuccess(Object result) {
        if (result instanceof TransactionStatus status) {
            // Query returns FAILED both for upstream error and business failed status.
            // Keep conservative behavior: FAILED => false, others => true.
            return !TransactionStatus.FAILED.equals(status);
        }
        return result != null;
    }
}
