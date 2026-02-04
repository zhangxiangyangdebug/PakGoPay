package com.pakgopay.service.transaction.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.pakgopay.util.IpAddressUtil;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class ColThirdPartyAlipayHandler extends OrderHandler {

    private static final String CHANNEL_CODE = "alipay";

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public Object handle(Object request) {
        Map<String, Object> payload = toPayload(request);
        Map<String, Object> finalPayload = buildPayload(payload);
        validateRequiredFields(finalPayload);
        String url = resolveDirectUrl(payload);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(finalPayload, jsonHeadersWithApiKey(payload));
        log.info("ColThirdPartyAlipayHandler handle, channelCode={}, url={}, request={}", CHANNEL_CODE, url, finalPayload);
        PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, url);
        adaptResponse(response);
        log.info("third-party collection response, channelCode={}, code={}", CHANNEL_CODE, response == null ? null : response.getCode());
        return response;
    }

    @Override
    public NotifyRequest handleNotify(String body) {
        Map<String, Object> payload = parseBodyMap(body);
        log.info("third-party collection notify, channelCode={}, payload={}", CHANNEL_CODE, payload);
        return buildNotifyResponse(body);
    }

    private Map<String, Object> toPayload(Object request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        if (request instanceof Map) {
            return (Map<String, Object>) request;
        }
        return new ObjectMapper().convertValue(request, Map.class);
    }

    private HttpHeaders jsonHeadersWithApiKey(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = resolveApiKey(payload);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("missing required field: apiKey");
        }
        headers.set("Authorization", "api-key " + apiKey.trim());
        return headers;
    }

    private Map<String, Object> buildPayload(Map<String, Object> payload) {
        Map<String, Object> result = new ObjectMapper().convertValue(payload, Map.class);
        Map<String, Object> params = extractChannelParams(payload);
        result.put("mid", resolveValue(params, "mid", payload.get("mid")));
        result.put("amount", resolveAmountString(payload.get("amount")));
        result.put("order_no", resolveValue(params, "orderNo", payload.get("merchantOrderNo")));
        result.put("gateway", resolveValue(params, "gateway", payload.get("gateway")));
        result.put("ip", IpAddressUtil.resolveServerIp());
        result.put("notify_url", resolveValue(params, "notifyUrl", payload.get("callbackUrl")));
        result.put("return_url", resolveValue(params, "returnUrl", payload.get("returnUrl")));
        result.put("name", resolveValue(params, "name", payload.get("name")));
        result.put("bank_name", resolveValue(params, "bankName", payload.get("bank_name")));
        result.put("card_no", resolveValue(params, "cardNo", payload.get("card_no")));
        result.put("card_name", resolveValue(params, "cardName", payload.get("card_name")));
        result.put("sign", resolveValue(params, "sign", payload.get("sign")));
        return result;
    }

    private void validateRequiredFields(Map<String, Object> payload) {
        requireNonBlank(payload, "mid");
        requireNonBlank(payload, "amount");
        requireNonBlank(payload, "order_no");
        requireNonBlank(payload, "gateway");
        requireNonBlank(payload, "ip");
        requireNonBlank(payload, "notify_url");
        requireNonBlank(payload, "return_url");
        requireNonBlank(payload, "name");
        requireNonBlank(payload, "bank_name");
        requireNonBlank(payload, "card_no");
        requireNonBlank(payload, "card_name");
        requireNonBlank(payload, "sign");
    }

    private void requireNonBlank(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("missing required field: " + key);
        }
    }

    private String resolveDirectUrl(Map<String, Object> payload) {
        Object value = payload.get("paymentRequestCollectionUrl");
        if (value == null) {
            Object channelParams = payload.get("channelParams");
            if (channelParams instanceof Map<?, ?> params) {
                value = params.get("paymentRequestCollectionUrl");
            }
        }
        if (value == null) {
            throw new IllegalArgumentException("missing required field: paymentRequestCollectionUrl");
        }
        return String.valueOf(value);
    }

    private String resolveApiKey(Map<String, Object> payload) {
        Object raw = resolveValue(extractChannelParams(payload), "apiKey", payload.get("apiKey"));
        if (raw != null) {
            return String.valueOf(raw);
        }
        Object interfaceParam = payload.get("collectionInterfaceParam");
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

    private Map<String, Object> extractChannelParams(Map<String, Object> payload) {
        Object channelParams = payload.get("channelParams");
        if (channelParams instanceof Map<?, ?> params) {
            return (Map<String, Object>) params;
        }
        return Collections.emptyMap();
    }

    private Object resolveValue(Map<String, Object> params, String key, Object fallback) {
        Object value = params.get(key);
        return value == null ? fallback : value;
    }

    private String resolveAmountString(Object amount) {
        if (amount == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(amount))
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (Exception e) {
            return String.valueOf(amount);
        }
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

    // resolveServerIp moved to IpUtil
}
