package com.pakgopay.service.transaction.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.transaction.OrderHandler;
import com.pakgopay.util.CryptoUtil;
import com.pakgopay.util.IpAddressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class ThirdPartyAlipayHandler extends OrderHandler {

    private static final String CHANNEL_CODE = "alipay";

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public Object handleCol(CollectionCreateEntity request) {
        request = requireRequest(request, "collection request is null");
        Map<String, Object> finalPayload = buildPayload(request);
        validateColRequiredFields(finalPayload);
        String url = resolveDirectUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(request));
        log.info("ColThirdPartyAlipayHandler handle, channelCode={}, url={}, request={}", CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponse(response);
        log.info("third-party collection response, channelCode={}, code={}, response={}", CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return response;
    }

    @Override
    public Object handlePay(PayCreateEntity request) {
        request = requireRequest(request, "pay request is null");
        Map<String, Object> finalPayload = buildPayPayload(request);
        validatePayRequiredFields(finalPayload);
        String url = resolvePayUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(request));
        log.info("ThirdPartyAlipayHandler handlePay, channelCode={}, url={}, request={}", CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponse(response);
        log.info("third-party payout response, channelCode={}, code={}, response={}", CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return response;
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        request = requireRequest(request, "collection query request is null");
        Map<String, Object> finalPayload = buildCollectionQueryPayload(request);
        validateQueryRequiredFields(finalPayload);
        String url = resolveCollectionQueryUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(request));
        log.info("ThirdPartyAlipayHandler handleCollectionQuery, channelCode={}, url={}, request={}",
                CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponse(response);
        log.info("third-party collection query response, channelCode={}, code={}, response={}",
                CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return resolveQueryStatus(response);
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        request = requireRequest(request, "pay query request is null");
        Map<String, Object> finalPayload = buildPayQueryPayload(request);
        validateQueryRequiredFields(finalPayload);
        String url = resolvePayQueryUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(request));
        log.info("ThirdPartyAlipayHandler handlePayQuery, channelCode={}, url={}, request={}",
                CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponse(response);
        log.info("third-party payout query response, channelCode={}, code={}, response={}",
                CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return resolveQueryStatus(response);
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body, String sign) {
        verifyNotifySign(body, sign);
        log.info("third-party collection notify, channelCode={}, payload={}", CHANNEL_CODE, body);
        return buildNotifyResponse(body);
    }

    @Override
    public Object getNotifySuccessResponse(){
        return "ok";
    }

    @Override
    public NotifyResult sendNotifyToMerchant(Map<String, Object> body, String url) {
        if (url == null || url.isBlank()) {
            log.warn("sendNotifyToMerchant skipped, channelCode={}, reason=callback_url_empty", CHANNEL_CODE);
            return new NotifyResult(false, 1);
        }
        Map<String, Object> payload = body == null ? new HashMap<>() : body;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        int maxAttempts = 5;
        long delayMs = 500L;
        long maxDelayMs = 8000L;
        int failedAttempts = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
                if (response != null && Integer.valueOf(200).equals(response.getCode())) {
                    log.info("sendNotifyToMerchant success, channelCode={}, url={}, attempt={}, responseCode={}",
                            CHANNEL_CODE, url, attempt, response.getCode());
                    return new NotifyResult(true, failedAttempts);
                }
                throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR,
                        "merchant notify response not success");
            } catch (Exception e) {
                failedAttempts++;
                if (attempt >= maxAttempts) {
                    log.error("sendNotifyToMerchant failed after retries, channelCode={}, url={}, attempts={}, message={}",
                            CHANNEL_CODE, url, maxAttempts, e.getMessage());
                    return new NotifyResult(false, failedAttempts);
                }
                long jitterMs = ThreadLocalRandom.current().nextLong(0, 201);
                long sleepMs = Math.min(delayMs, maxDelayMs) + jitterMs;
                log.warn("sendNotifyToMerchant retry, channelCode={}, url={}, attempt={}, nextDelayMs={}, message={}",
                        CHANNEL_CODE, url, attempt, sleepMs, e.getMessage());
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.error("sendNotifyToMerchant interrupted, channelCode={}, url={}, attempt={}",
                            CHANNEL_CODE, url, attempt);
                    return new NotifyResult(false, failedAttempts);
                }
                delayMs = Math.min(delayMs * 2, maxDelayMs);
            }
        }
        return new NotifyResult(false, failedAttempts);
    }

    @Override
    public NotifyRequest buildNotifyResponse(Map<String, Object> bodyMap) {
        NotifyRequest response = super.buildNotifyResponse(bodyMap);
        response.setStatus(convertStatus(response.getStatus()).getMessage());
        return response;
    }

    private HttpHeaders jsonHeadersWithApiKey(Object request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
//        String apiKey = resolveApiKey(payload);
//        if (apiKey == null || apiKey.isBlank()) {
//            throw new IllegalArgumentException("missing required field: apiKey");
//        }
        String apiKey = "021fdff9059411f0";
        headers.set("Authorization", "api-key " + apiKey.trim());
        return headers;
    }

    private Map<String, Object> buildPayload(CollectionCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("mid", 24);
        result.put("amount", resolveAmountString(payload.getAmount()));
        result.put("order_no", resolveValue(params, "orderNo", payload.getMerchantOrderNo()));
        result.put("gateway", CHANNEL_CODE);
        result.put("ip", IpAddressUtil.resolveServerIp());
        result.put("notify_url", resolveValue(params, "notifyUrl", payload.getCallbackUrl()));
        result.put("return_url", resolveValue(params, "returnUrl", null));
        result.put("name", resolveValue(params, "name", null));
        result.put("bank_name", resolveValue(params, "bankName", params.get("bank_name")));
        result.put("card_no", resolveValue(params, "cardNo", params.get("card_no")));
        result.put("card_name", resolveValue(params, "cardName", params.get("card_name")));
        result.put("sign", CryptoUtil.signHmacSha256Base64(result,"75b7cb58f2f9fc7cf477172364c4ff39"));
        return result;
    }

    private Map<String, Object> buildPayPayload(PayCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("mid", 24);
        result.put("amount", resolveAmountString(payload.getAmount()));
        result.put("order_no", resolveValue(params, "orderNo", payload.getMerchantOrderNo()));
        result.put("ip", resolveValue(params, "ip", payload.getIp()));
        result.put("notify_url", resolveValue(params, "notifyUrl", payload.getCallbackUrl()));
        result.put("bank_code", "SHB");
        result.put("card_no", "123123123123123123123");
        result.put("holder_name", "bob");
//        result.put("bank_name", resolveValue(params, "bankName", payload.get("bank_name")));
//        result.put("bank_branch", resolveValue(params, "bankBranch", payload.get("bank_branch")));
//        result.put("identity_no", resolveValue(params, "identityNo", payload.get("identity_no")));
//        result.put("withdrawQueryUrl", resolveValue(params, "withdrawQueryUrl", payload.get("withdrawQueryUrl")));
        if (result.get("ip") == null) {
            result.put("ip", IpAddressUtil.resolveServerIp());
        }
        result.put("sign", CryptoUtil.signHmacSha256Base64(result, "75b7cb58f2f9fc7cf477172364c4ff39"));
        return result;
    }

    private Map<String, Object> buildCollectionQueryPayload(CollectionQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("order_no", resolveValue(params, "order_no", payload.getTransactionNo()));
        result.put("sign", resolveValue(params, "sign", payload.getSign()));
        return result;
    }

    private Map<String, Object> buildPayQueryPayload(PayQueryEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
        result.put("order_no", resolveValue(params, "order_no", payload.getTransactionNo()));
        result.put("sign", resolveValue(params, "sign", payload.getSign()));
        return result;
    }

    private void validateColRequiredFields(Map<String, Object> payload) {
        requireNonBlank(payload, "mid");
        requireNonBlank(payload, "amount");
        requireNonBlank(payload, "order_no");
        requireNonBlank(payload, "gateway");
        requireNonBlank(payload, "ip");
        requireNonBlank(payload, "notify_url");
        requireNonBlank(payload, "sign");
    }

    private void validatePayRequiredFields(Map<String, Object> payload) {
        requireNonBlank(payload, "mid");
        requireNonBlank(payload, "amount");
        requireNonBlank(payload, "order_no");
        requireNonBlank(payload, "ip");
        requireNonBlank(payload, "notify_url");
        requireNonBlank(payload, "bank_code");
        requireNonBlank(payload, "card_no");
        requireNonBlank(payload, "holder_name");
//        requireNonBlank(payload, "bank_name");
//        requireNonBlank(payload, "bank_branch");
//        requireNonBlank(payload, "identity_no");
        requireNonBlank(payload, "sign");
    }

    private void validateQueryRequiredFields(Map<String, Object> payload) {
        requireNonBlank(payload, "order_no");
        requireNonBlank(payload, "sign");
    }

    private String resolveApiKey(CollectionCreateEntity payload) {
        Object raw = resolveValue(extractChannelParams(payload), "apiKey", null);
        if (raw != null) {
            return String.valueOf(raw);
        }
        Object interfaceParam = payload.getCollectionInterfaceParam();
        if (interfaceParam instanceof String str && !str.isBlank()) {
            try {
                Map<String, Object> parsed = new ObjectMapper().readValue(str, Map.class);
                Object apiKey = parsed.get("apiKey");
                if (apiKey != null) {
                    return String.valueOf(apiKey);
                }
            } catch (Exception e) {
                log.warn("parse collectionInterfaceParam failed: {}", e.getMessage());
            }
        }
        return null;
    }

    private void adaptResponse(PaymentHttpResponse response) {
        if (response == null) {
            return;
        }
        Object data = response.getData();
        if (data instanceof Map<?, ?> map) {
            Object url = map.get("url");
            if (url != null && !map.containsKey("payUrl")) {
                ((Map<String, Object>) map).put("payUrl", url);
            }
        }
    }

    private TransactionStatus resolveQueryStatus(PaymentHttpResponse response) {
        if (response == null) {
            return TransactionStatus.FAILED;
        }
        Map<String, Object> data = null;
        Object rawData = response.getData();
        if (rawData instanceof Map<?, ?> map) {
            data = (Map<String, Object>) map;
        }
        Object statusObj = data == null ? null : data.get("status");
        String status = statusObj == null ? null : String.valueOf(statusObj);
        if (status == null || status.isBlank()) {
            return TransactionStatus.FAILED;
        }
        return convertStatus(status);
    }

    private TransactionStatus convertStatus(String status) {
        switch (status){
            case "succeeded":
                return TransactionStatus.SUCCESS;
            case "inprogress":
                return TransactionStatus.PROCESSING;
            case "failed":
                return TransactionStatus.FAILED;
            default:
                return TransactionStatus.FAILED;
        }
    }

    @SuppressWarnings("unchecked")
    private void verifyNotifySign(Map<String, Object> body, String signKey) {
        Object notifySign = body.remove("sign");
        if (notifySign == null || String.valueOf(notifySign).isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "notify sign is empty");
        }
        if (signKey == null || signKey.isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "notify sign key is empty");
        }
        signKey = "75b7cb58f2f9fc7cf477172364c4ff39";
        String expected = CryptoUtil.signHmacSha256Base64(body, signKey);
        if (expected == null || !expected.equals(String.valueOf(notifySign))) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "notify sign invalid");
        }
    }
}
