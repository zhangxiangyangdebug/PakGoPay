package com.pakgopay.service.transaction.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.service.common.SystemConfigGroupService;
import com.pakgopay.service.transaction.OrderHandler;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public abstract class AbstractThirdPartySignedHandler extends OrderHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_CALLBACK_RETRY_TIMES = 5;
    private static final String FIELD_AVAILABLE_BALANCE = "available_balance";
    private static final String FIELD_TRANSACTION_NO = "transactionNo";
    private static final String FIELD_MID = "mid";
    private static final String FIELD_SIGN = "sign";
    private static final String FIELD_URL = "url";
    private static final String FIELD_PAY_URL = "payUrl";

    @Autowired
    protected RestTemplateUtil restTemplateUtil;
    @Autowired
    protected SystemConfigGroupService systemConfigGroupService;

    protected String resolveMerchantId(ChannelCredential credential) {
        return credential == null ? null : credential.merchantId;
    }

    protected HttpEntity<Map<String, Object>> buildSignedRequestEntity(
            String interfaceParam,
            String scene,
            Map<String, Object> payload) {
        ChannelCredential credential = resolveChannelCredential(interfaceParam, scene);
        applyCredential(payload, credential);
        return new HttpEntity<>(payload, jsonHeadersWithApiKey(credential.apiKey));
    }

    protected HttpEntity<Map<String, Object>> buildNotifyRequestEntity(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    protected PaymentHttpResponse postForCreate(
            HttpEntity<Map<String, Object>> entity,
            String url,
            String handlerName,
            String channelCode) {
        log.info("{} handleCreate, channelCode={}, url={}, request={}", handlerName, channelCode, url, entity.getBody());
        PaymentHttpResponse rawResponse = restTemplateUtil.request(entity, HttpMethod.POST, url);
        PaymentHttpResponse response = normalizeCreateResponse(rawResponse);
        log.info("third-party create response, channelCode={}, code={}, rawCode={}, response={}",
                channelCode, response.getCode(), rawResponse == null ? null : rawResponse.getCode(), response);
        return response;
    }

    protected TransactionStatus postForQuery(
            HttpEntity<Map<String, Object>> entity,
            String url,
            String handlerName,
            String channelCode) {
        log.info("{} handleQuery, channelCode={}, url={}, request={}", handlerName, channelCode, url, entity.getBody());
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponseData(response);
        log.info("third-party query response, channelCode={}, code={}, response={}",
                channelCode, response == null ? null : response.getCode(), response);
        return resolveQueryStatus(response);
    }

    protected PaymentHttpResponse postForBalance(
            HttpEntity<Map<String, Object>> entity,
            String url,
            String handlerName,
            String channelCode) {
        log.info("{} handleBalance, channelCode={}, url={}, request={}",
                handlerName, channelCode, url, entity.getBody());
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        log.info("third-party balance response, channelCode={}, code={}, response={}",
                channelCode, response == null ? null : response.getCode(), response);
        return response;
    }

    @SuppressWarnings("unchecked")
    protected BigDecimal resolveAvailableBalance(PaymentHttpResponse response) {
        if (response == null || response.getCode() == null
                || !(Integer.valueOf(200).equals(response.getCode()) || Integer.valueOf(0).equals(response.getCode()))) {
            return null;
        }
        Object data = response.getData();
        if (!(data instanceof Map<?, ?> rawMap)) {
            return null;
        }
        String value = firstNonBlank((Map<String, Object>) rawMap, FIELD_AVAILABLE_BALANCE);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    protected NotifyRequest handleNotifyInternal(
            Map<String, Object> body,
            String interfaceParam,
            String channelCode) {
        ChannelCredential credential = resolveChannelCredential(interfaceParam, "notify");
        verifyNotifySign(body, credential.signKey);
        log.info("third-party notify, channelCode={}, payload={}", channelCode, body);
        return buildNotifyResponse(body);
    }

    protected Object getNotifySuccessResponseInternal() {
        return "ok";
    }

    protected NotifyResult sendNotifyToMerchantInternal(
            Map<String, Object> body,
            String url,
            String channelCode) {
        if (url == null || url.isBlank()) {
            log.warn("sendNotifyToMerchant skipped, channelCode={}, reason=callback_url_empty", channelCode);
            return new NotifyResult(false, 1);
        }
        Map<String, Object> payload = body == null ? new HashMap<>() : body;
        HttpEntity<Map<String, Object>> entity = buildNotifyRequestEntity(payload);
        int maxAttempts = resolveCallbackRetryTimes(payload);
        long delayMs = 500L;
        long maxDelayMs = 8000L;
        int failedAttempts = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
                if (response != null && Integer.valueOf(200).equals(response.getCode())) {
                    log.info("sendNotifyToMerchant success, channelCode={}, url={}, attempt={}, responseCode={}",
                            channelCode, url, attempt, response.getCode());
                    return new NotifyResult(true, failedAttempts);
                }
                throw new PakGoPayException(ResultCode.HTTP_REQUEST_ERROR, "merchant notify response not success");
            } catch (Exception e) {
                failedAttempts++;
                if (attempt >= maxAttempts) {
                    log.error("sendNotifyToMerchant failed after retries, channelCode={}, url={}, attempts={}, message={}",
                            channelCode, url, maxAttempts, e.getMessage());
                    return new NotifyResult(false, failedAttempts);
                }
                long jitterMs = ThreadLocalRandom.current().nextLong(0, 201);
                long sleepMs = Math.min(delayMs, maxDelayMs) + jitterMs;
                log.warn("sendNotifyToMerchant retry, channelCode={}, url={}, attempt={}, nextDelayMs={}, message={}",
                        channelCode, url, attempt, sleepMs, e.getMessage());
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.error("sendNotifyToMerchant interrupted, channelCode={}, url={}, attempt={}",
                            channelCode, url, attempt);
                    return new NotifyResult(false, failedAttempts);
                }
                delayMs = Math.min(delayMs * 2, maxDelayMs);
            }
        }
        return new NotifyResult(false, failedAttempts);
    }

    private int resolveCallbackRetryTimes(Map<String, Object> payload) {
        String transactionNo = payload == null ? null : firstNonBlank(payload, FIELD_TRANSACTION_NO);
        SystemConfigItemKeyEnum retryKey = isPayoutTransaction(transactionNo)
                ? SystemConfigItemKeyEnum.PAYOUT_CALLBACK_RETRY_TIMES
                : SystemConfigItemKeyEnum.COLLECTION_CALLBACK_RETRY_TIMES;
        Integer retryTimes = systemConfigGroupService.getConfigValue(
                retryKey, Integer.class, DEFAULT_CALLBACK_RETRY_TIMES);
        return retryTimes == null || retryTimes <= 0 ? DEFAULT_CALLBACK_RETRY_TIMES : retryTimes;
    }

    private boolean isPayoutTransaction(String transactionNo) {
        return CommonUtil.isPayoutTransactionNo(transactionNo);
    }

    protected NotifyRequest buildNotifyResponseInternal(Map<String, Object> bodyMap) {
        NotifyRequest response = super.buildNotifyResponse(bodyMap);
        response.setStatus(convertStatus(response.getStatus()).getMessage());
        return response;
    }

    protected void validateRequiredFields(Map<String, Object> payload, List<String> requiredFields) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }
        for (String field : requiredFields) {
            requireNonBlank(payload, field);
        }
    }

    protected ChannelCredential resolveChannelCredential(String interfaceParams, String scene) {
        Map<String, Object> parsed = parseInterfaceParams(interfaceParams, scene);
        return resolveCredential(parsed, scene);
    }

    protected ChannelCredential resolveCredential(Map<String, Object> interfaceParams, String scene) {
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

    protected String resolveAsString(Map<String, Object> source, String key) {
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

    protected Map<String, Object> parseInterfaceParams(String interfaceParam, String scene) {
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

    protected void applyCredential(Map<String, Object> payload, ChannelCredential credential) {
        payload.put(FIELD_MID, resolveMerchantId(credential));
        payload.put(FIELD_SIGN, CryptoUtil.signThirdPartyHmacSha1Base64(payload, credential.signKey));
    }

    protected HttpHeaders jsonHeadersWithApiKey(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "api-key " + apiKey.trim());
        return headers;
    }

    protected void adaptResponseData(PaymentHttpResponse response) {
        if (response == null) {
            return;
        }
        Object data = response.getData();
        if (data instanceof Map<?, ?> map) {
            Object url = map.get(FIELD_URL);
            if (url != null && !map.containsKey(FIELD_PAY_URL)) {
                ((Map<String, Object>) map).put(FIELD_PAY_URL, url);
            }
        }
    }

    protected PaymentHttpResponse normalizeCreateResponse(PaymentHttpResponse rawResponse) {
        adaptResponseData(rawResponse);
        PaymentHttpResponse response = new PaymentHttpResponse();
        boolean success = rawResponse != null && Integer.valueOf(200).equals(rawResponse.getCode());
        response.setCode(success ? 0 : -1);
        response.setMessage(success ? "success" : (rawResponse == null ? "request failed" : rawResponse.getMessage()));
        response.setData(rawResponse == null ? null : rawResponse.getData());
        return response;
    }

    protected TransactionStatus resolveQueryStatus(PaymentHttpResponse response) {
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

    protected TransactionStatus convertStatus(String status) {
        switch (status) {
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
    protected void verifyNotifySign(Map<String, Object> body, String signKey) {
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

    protected static class ChannelCredential {
        protected final String merchantId;
        protected final String apiKey;
        protected final String signKey;

        protected ChannelCredential(String merchantId, String apiKey, String signKey) {
            this.merchantId = merchantId;
            this.apiKey = apiKey;
            this.signKey = signKey;
        }
    }
}
