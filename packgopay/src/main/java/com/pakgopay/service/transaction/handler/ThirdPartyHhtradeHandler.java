package com.pakgopay.service.transaction.handler;

import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.common.OrderFlowLogSession;
import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.util.IpAddressUtil;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class ThirdPartyHhtradeHandler extends AbstractThirdPartySignedHandler {
    private static final String CHANNEL_CODE = "gcash_wake";

    @Override
    public PaymentHttpResponse handleCol(CollectionCreateEntity request) {
        Map<String, Object> payload = buildCollectionPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getCollectionInterfaceParam(), "collection", payload);
        validateRequiredFields(payload, Arrays.asList(
                "mid", "amount", "order_no", "gateway", "ip", "notify_url", "sign"));
        return postForCreate(entity, resolveDirectUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public PaymentHttpResponse handlePay(PayCreateEntity request) {
        Map<String, Object> payload = buildPayPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay", payload);
        validateRequiredFields(payload, Arrays.asList(
                "mid", "amount", "order_no", "ip", "notify_url",
                "bank_code", "card_no", "holder_name", "sign"));
        return postForCreate(entity, resolvePayUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        Map<String, Object> payload = buildCollectionQueryPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getCollectionInterfaceParam(), "collection query", payload);
        validateRequiredFields(payload, Arrays.asList("order_no", "sign"));
        return postForQuery(entity, resolveCollectionQueryUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        Map<String, Object> payload = buildPayQueryPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay query", payload);
        validateRequiredFields(payload, Arrays.asList("order_no", "sign"));
        return postForQuery(entity, resolvePayQueryUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public BigDecimal queryPayBalance(PayCreateEntity request, OrderFlowLogSession flowSession) {
        Map<String, Object> payload = new HashMap<>();
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay balance query", payload);
        validateRequiredFields(payload, Arrays.asList("mid", "sign"));
        if (flowSession != null) {
            flowSession.add(OrderFlowStepEnum.THIRD_BALANCE_REQUEST, true, entity.getBody());
        }
        PaymentHttpResponse response = postForBalance(
                entity, resolvePayBalanceUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
        if (flowSession != null) {
            flowSession.add(OrderFlowStepEnum.THIRD_BALANCE_RESPONSE, true, response);
        }
        return resolveAvailableBalance(response);
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body, String interfaceParam) {
        return handleNotifyInternal(body, interfaceParam, CHANNEL_CODE);
    }

    @Override
    public Object getNotifySuccessResponse() {
        return getNotifySuccessResponseInternal();
    }

    @Override
    public NotifyResult sendNotifyToMerchant(Map<String, Object> body, String url) {
        return sendNotifyToMerchantInternal(body, url, CHANNEL_CODE);
    }

    @Override
    public NotifyRequest buildNotifyResponse(Map<String, Object> bodyMap) {
        return buildNotifyResponseInternal(bodyMap);
    }

    private Map<String, Object> buildCollectionPayload(CollectionCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("amount", resolveAmountString(payload.getAmount()));
        result.put("order_no", resolveValue(params, "orderNo", payload.getMerchantOrderNo()));
        result.put("gateway", CHANNEL_CODE);
        result.put("ip", IpAddressUtil.resolveServerIp());
        result.put("notify_url", resolveValue(params, "notifyUrl", payload.getCallbackUrl()));
        result.put("return_url", resolveValue(params, "returnUrl", null));
//        result.put("name", resolveValue(params, "name", null));
//        result.put("bank_name", resolveValue(params, "bankName", params.get("bank_name")));
//        result.put("card_no", resolveValue(params, "cardNo", params.get("card_no")));
//        result.put("card_name", resolveValue(params, "cardName", params.get("card_name")));
        return result;
    }

    private Map<String, Object> buildPayPayload(PayCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("amount", resolveAmountString(payload.getAmount()));
        result.put("order_no", resolveValue(params, "orderNo", payload.getMerchantOrderNo()));
        result.put("ip", resolveValue(params, "ip", payload.getIp()));
        result.put("notify_url", resolveValue(params, "notifyUrl", payload.getCallbackUrl()));
        result.put("bank_code", "PH_MSB");
        result.put("card_no", "123123123123123123123");
        result.put("holder_name", "bob");
        if (result.get("ip") == null) {
            result.put("ip", IpAddressUtil.resolveServerIp());
        }
        return result;
    }

    private Map<String, Object> buildCollectionQueryPayload(CollectionQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("order_no", resolveValue(params, "order_no", payload.getTransactionNo()));
        return result;
    }

    private Map<String, Object> buildPayQueryPayload(PayQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("order_no", resolveValue(params, "order_no", payload.getTransactionNo()));
        return result;
    }

}
