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
    private static final String FIELD_MID = "mid";
    private static final String FIELD_SIGN = "sign";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_ORDER_NO = "order_no";
    private static final String FIELD_GATEWAY = "gateway";
    private static final String FIELD_IP = "ip";
    private static final String FIELD_NOTIFY_URL = "notify_url";
    private static final String FIELD_RETURN_URL = "return_url";
    private static final String FIELD_BANK_CODE = "bank_code";
    private static final String FIELD_CARD_NO = "card_no";
    private static final String FIELD_HOLDER_NAME = "holder_name";
    private static final String PARAM_ORDER_NO = "orderNo";
    private static final String PARAM_NOTIFY_URL = "notifyUrl";
    private static final String PARAM_RETURN_URL = "returnUrl";
    private static final String BANK_CODE_PH_MSB = "PH_MSB";
    private static final String DEFAULT_CARD_NO = "123123123123123123123";
    private static final String DEFAULT_HOLDER_NAME = "bob";

    @Override
    public PaymentHttpResponse handleCol(CollectionCreateEntity request) {
        Map<String, Object> payload = buildCollectionPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getCollectionInterfaceParam(), "collection", payload);
        validateRequiredFields(payload, Arrays.asList(
                FIELD_MID, FIELD_AMOUNT, FIELD_ORDER_NO, FIELD_GATEWAY, FIELD_IP, FIELD_NOTIFY_URL, FIELD_SIGN));
        return postForCreate(entity, resolveDirectUrl(request), getClass().getSimpleName(), request.getChannelCode());
    }

    @Override
    public PaymentHttpResponse handlePay(PayCreateEntity request) {
        Map<String, Object> payload = buildPayPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay", payload);
        validateRequiredFields(payload, Arrays.asList(
                FIELD_MID, FIELD_AMOUNT, FIELD_ORDER_NO, FIELD_IP, FIELD_NOTIFY_URL,
                FIELD_BANK_CODE, FIELD_CARD_NO, FIELD_HOLDER_NAME, FIELD_SIGN));
        return postForCreate(entity, resolvePayUrl(request), getClass().getSimpleName(), request.getChannelCode());
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        Map<String, Object> payload = buildCollectionQueryPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getCollectionInterfaceParam(), "collection query", payload);
        validateRequiredFields(payload, Arrays.asList(FIELD_ORDER_NO, FIELD_SIGN));
        return postForQuery(entity, resolveCollectionQueryUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        Map<String, Object> payload = buildPayQueryPayload(request);
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay query", payload);
        validateRequiredFields(payload, Arrays.asList(FIELD_ORDER_NO, FIELD_SIGN));
        return postForQuery(entity, resolvePayQueryUrl(request), getClass().getSimpleName(), CHANNEL_CODE);
    }

    @Override
    public BigDecimal queryPayBalance(PayCreateEntity request, OrderFlowLogSession flowSession) {
        Map<String, Object> payload = new HashMap<>();
        HttpEntity<Map<String, Object>> entity = buildSignedRequestEntity(
                request.getPayInterfaceParam(), "pay balance query", payload);
        validateRequiredFields(payload, Arrays.asList(FIELD_MID, FIELD_SIGN));
        if (flowSession != null) {
            flowSession.add(OrderFlowStepEnum.THIRD_BALANCE_REQUEST, true, entity.getBody());
        }
        PaymentHttpResponse response = postForBalance(
                entity, resolvePayBalanceUrl(request), getClass().getSimpleName(), request.getChannelCode());
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
        result.put(FIELD_AMOUNT, resolveAmountString(payload.getAmount()));
        result.put(FIELD_ORDER_NO, resolveValue(params, PARAM_ORDER_NO, payload.getMerchantOrderNo()));
        result.put(FIELD_GATEWAY, payload.getChannelCode());
        result.put(FIELD_IP, IpAddressUtil.resolveServerIp());
        result.put(FIELD_NOTIFY_URL, resolveValue(params, PARAM_NOTIFY_URL, payload.getCallbackUrl()));
        result.put(FIELD_RETURN_URL, resolveValue(params, PARAM_RETURN_URL, null));
//        result.put("name", resolveValue(params, "name", null));
//        result.put("bank_name", resolveValue(params, "bankName", params.get("bank_name")));
//        result.put("card_no", resolveValue(params, "cardNo", params.get("card_no")));
//        result.put("card_name", resolveValue(params, "cardName", params.get("card_name")));
        return result;
    }

    private Map<String, Object> buildPayPayload(PayCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put(FIELD_AMOUNT, resolveAmountString(payload.getAmount()));
        result.put(FIELD_ORDER_NO, resolveValue(params, PARAM_ORDER_NO, payload.getMerchantOrderNo()));
        result.put(FIELD_IP, resolveValue(params, FIELD_IP, payload.getIp()));
        result.put(FIELD_NOTIFY_URL, resolveValue(params, PARAM_NOTIFY_URL, payload.getCallbackUrl()));
        result.put(FIELD_BANK_CODE, BANK_CODE_PH_MSB);
        result.put(FIELD_CARD_NO, DEFAULT_CARD_NO);
        result.put(FIELD_HOLDER_NAME, DEFAULT_HOLDER_NAME);
        if (result.get(FIELD_IP) == null) {
            result.put(FIELD_IP, IpAddressUtil.resolveServerIp());
        }
        return result;
    }

    private Map<String, Object> buildCollectionQueryPayload(CollectionQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put(FIELD_ORDER_NO, resolveValue(params, FIELD_ORDER_NO, payload.getTransactionNo()));
        return result;
    }

    private Map<String, Object> buildPayQueryPayload(PayQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put(FIELD_ORDER_NO, resolveValue(params, FIELD_ORDER_NO, payload.getTransactionNo()));
        return result;
    }

}
