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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public PaymentHttpResponse handleCol(CollectionCreateEntity request) {
        request = requireRequest(request, "collection request is null");
        ChannelCredential credential = resolveChannelCredential(
                request.getCollectionInterfaceParam(),
                "collection");
        Map<String, Object> finalPayload = buildPayload(request);
        applyCredential(finalPayload, credential);
        validateColRequiredFields(finalPayload);
        String url = resolveDirectUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(credential.apiKey));
        log.info("ColThirdPartyAlipayHandler handle, channelCode={}, url={}, request={}", CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse rawResponse = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = normalizeCreateResponse(rawResponse);
        log.info("third-party collection response, channelCode={}, code={}, rawCode={}, response={}",
                CHANNEL_CODE, response.getCode(), rawResponse == null ? null : rawResponse.getCode(), response);
        return response;
    }

    @Override
    public PaymentHttpResponse handlePay(PayCreateEntity request) {
        request = requireRequest(request, "pay request is null");
        ChannelCredential credential = resolveChannelCredential(
                request.getPayInterfaceParam(),
                "pay");
        Map<String, Object> finalPayload = buildPayPayload(request);
        applyCredential(finalPayload, credential);
        validatePayRequiredFields(finalPayload);
        String url = resolvePayUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(credential.apiKey));
        log.info("ThirdPartyAlipayHandler handlePay, channelCode={}, url={}, request={}", CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse rawResponse = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = normalizeCreateResponse(rawResponse);
        log.info("third-party payout response, channelCode={}, code={}, rawCode={}, response={}",
                CHANNEL_CODE, response.getCode(), rawResponse == null ? null : rawResponse.getCode(), response);
        return response;
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        request = requireRequest(request, "collection query request is null");
        ChannelCredential credential = resolveChannelCredential(
                request.getCollectionInterfaceParam(),
                "collection query");
        Map<String, Object> finalPayload = buildCollectionQueryPayload(request);
        applyCredential(finalPayload, credential);
        validateQueryRequiredFields(finalPayload);
        String url = resolveCollectionQueryUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(credential.apiKey));
        log.info("ThirdPartyAlipayHandler handleCollectionQuery, channelCode={}, url={}, request={}",
                CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponseData(response);
        log.info("third-party collection query response, channelCode={}, code={}, response={}",
                CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return resolveQueryStatus(response);
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        request = requireRequest(request, "pay query request is null");
        ChannelCredential credential = resolveChannelCredential(
                request.getPayInterfaceParam(),
                "pay query");
        Map<String, Object> finalPayload = buildPayQueryPayload(request);
        applyCredential(finalPayload, credential);
        validateQueryRequiredFields(finalPayload);
        String url = resolvePayQueryUrl(request);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(credential.apiKey));
        log.info("ThirdPartyAlipayHandler handlePayQuery, channelCode={}, url={}, request={}",
                CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponseData(response);
        log.info("third-party payout query response, channelCode={}, code={}, response={}",
                CHANNEL_CODE, response == null ? null : response.getCode(), response);
        return resolveQueryStatus(response);
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body, String interfaceParam) {
        ChannelCredential credential = resolveChannelCredential(interfaceParam, "notify");
        verifyNotifySign(body, credential.signKey);
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

    private HttpHeaders jsonHeadersWithApiKey(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "api-key " + apiKey.trim());
        return headers;
    }

    private Map<String, Object> buildPayload(CollectionCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
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
        return result;
    }

    private Map<String, Object> buildPayPayload(PayCreateEntity payload) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> params = extractChannelParams(payload);
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

    private ChannelCredential resolveChannelCredential(String interfaceParams, String scene) {
        Map<String, Object> parsed = parseInterfaceParams(interfaceParams, scene);
        return resolveCredential(parsed, scene);
    }

    private ChannelCredential resolveCredential(Map<String, Object> interfaceParams, String scene) {
        String merchantId = resolveAsString(interfaceParams, "merchantId");
        String apiKey = resolveAsString(interfaceParams, "apiKey");
        String signKey = resolveAsString(interfaceParams, "signKey");
        if (merchantId == null || merchantId.isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, scene + " missing merchantId");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, scene + " missing apiKey");
        }
        if (signKey == null || signKey.isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, scene + " missing signKey");
        }
        return new ChannelCredential(merchantId, apiKey, signKey);
    }

    private String resolveAsString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value != null) {
            String str = String.valueOf(value).trim();
            if (!str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    private Map<String, Object> parseInterfaceParams(String interfaceParam, String scene) {
        if (interfaceParam == null || interfaceParam.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(interfaceParam, Map.class);
        } catch (Exception e) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    scene + " interface param parse failed: " + e.getMessage());
        }
    }

    private void applyCredential(Map<String, Object> payload, ChannelCredential credential) {
        payload.put("mid", credential.merchantId);
        payload.put("sign", CryptoUtil.signThirdPartyHmacSha1Base64(payload, credential.signKey));
    }

    /**
     * Adapt third-party payload shape for internal unified response fields.
     */
    private void adaptResponseData(PaymentHttpResponse response) {
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

    /**
     * Normalize create-order response to business code semantics:
     * success=0, failed=-1.
     */
    private PaymentHttpResponse normalizeCreateResponse(PaymentHttpResponse rawResponse) {
        adaptResponseData(rawResponse);
        PaymentHttpResponse response = new PaymentHttpResponse();
        boolean success = rawResponse != null && Integer.valueOf(200).equals(rawResponse.getCode());
        response.setCode(success ? 0 : -1);
        response.setMessage(success ? "success" : (rawResponse == null ? "request failed" : rawResponse.getMessage()));
        response.setData(rawResponse == null ? null : rawResponse.getData());
        return response;
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
        String expected = CryptoUtil.signThirdPartyHmacSha1Base64(body, signKey);
        if (expected == null || !expected.equals(String.valueOf(notifySign))) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "notify sign invalid");
        }
    }

    private static class ChannelCredential {
        private final String merchantId;
        private final String apiKey;
        private final String signKey;

        private ChannelCredential(String merchantId, String apiKey, String signKey) {
            this.merchantId = merchantId;
            this.apiKey = apiKey;
            this.signKey = signKey;
        }
    }
}
