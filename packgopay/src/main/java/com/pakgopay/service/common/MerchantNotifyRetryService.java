package com.pakgopay.service.common;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.common.enums.SystemConfigItemKeyEnum;
import com.pakgopay.common.http.RestTemplateUtil;
import com.pakgopay.data.entity.transaction.MerchantNotifyRetryMessage;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MerchantNotifyRetryService {

    private static final int DEFAULT_CALLBACK_RETRY_TIMES = 5;
    private static final long RETRY_BASE_DELAY_MS = 500L;
    private static final long RETRY_MAX_DELAY_MS = 8000L;
    private static final String CB_OPEN_PREFIX = "merchant_notify:cb:open:";
    private static final String CB_FAIL_PREFIX = "merchant_notify:cb:fail:";
    private static final int CB_FAIL_THRESHOLD = 20;
    private static final int CB_FAIL_WINDOW_SECONDS = 60;
    private static final int CB_OPEN_SECONDS = 120;

    @Autowired
    private RestTemplateUtil restTemplateUtil;
    @Autowired
    private SendDmqMessage sendDmqMessage;
    @Autowired
    private SystemConfigGroupService systemConfigGroupService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CollectionOrderMapper collectionOrderMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private OrderFlowLogService orderFlowLogService;

    /**
     * First callback attempt for collection.
     */
    public void notifyCollectionFirstAttempt(String transactionNo, String callbackUrl, Map<String, Object> body) {
        notifyFirstAttempt(true, transactionNo, callbackUrl, body);
    }

    /**
     * First callback attempt for payout.
     */
    public void notifyPayoutFirstAttempt(String transactionNo, String callbackUrl, Map<String, Object> body) {
        notifyFirstAttempt(false, transactionNo, callbackUrl, body);
    }

    /**
     * Handle one MQ retry message.
     */
    public void handleRetryMessage(boolean collection, MerchantNotifyRetryMessage message) {
        if (message == null || message.getTransactionNo() == null || message.getTransactionNo().isBlank()) {
            log.warn("merchant notify retry skip invalid message, type={}", collection ? "collection" : "payout");
            return;
        }
        int attempt = message.getAttempt() == null || message.getAttempt() <= 0 ? 1 : message.getAttempt();
        int maxAttempts = message.getMaxAttempts() == null || message.getMaxAttempts() <= 0
                ? resolveCallbackRetryTimes(message.getTransactionNo())
                : message.getMaxAttempts();
        log.info("merchant notify retry consumed, type={}, transactionNo={}, attempt={}, maxAttempts={}",
                collection ? "collection" : "payout",
                message.getTransactionNo(),
                attempt,
                maxAttempts);
        handleAttempt(collection, message.getTransactionNo(), message.getCallbackUrl(), message.getBody(), attempt, maxAttempts);
    }

    private void notifyFirstAttempt(boolean collection, String transactionNo, String callbackUrl, Map<String, Object> body) {
        if (transactionNo == null || transactionNo.isBlank() || callbackUrl == null || callbackUrl.isBlank()) {
            log.info("merchant notify first attempt skipped, type={}, transactionNo={}, callbackUrlEmpty={}",
                    collection ? "collection" : "payout",
                    transactionNo,
                    callbackUrl == null || callbackUrl.isBlank());
            return;
        }
        int maxAttempts = resolveCallbackRetryTimes(transactionNo);
        log.info("merchant notify first attempt start, type={}, transactionNo={}, maxAttempts={}",
                collection ? "collection" : "payout",
                transactionNo,
                maxAttempts);
        handleAttempt(collection, transactionNo, callbackUrl, body, 1, maxAttempts);
    }

    private void handleAttempt(
            boolean collection,
            String transactionNo,
            String callbackUrl,
            Map<String, Object> body,
            int attempt,
            int maxAttempts) {
        // Reject invalid callback URL before any HTTP call/retry.
        if (!isValidHttpUrl(callbackUrl)) {
            log.warn("merchant notify invalid callbackUrl, type={}, transactionNo={}, callbackUrl={}",
                    collection ? "collection" : "payout", transactionNo, callbackUrl);
            finalizeCallback(collection, transactionNo, body, attempt, false, "invalid_callback_url");
            return;
        }

        // One attempt per invocation; retry scheduling is done by MQ.
        AttemptResult result = doSingleHttpAttempt(callbackUrl, body);
        if (result.success) {
            finalizeCallback(collection, transactionNo, body, attempt, true, result.message);
            return;
        }
        if (attempt >= maxAttempts) {
            finalizeCallback(collection, transactionNo, body, attempt, false, result.message);
            return;
        }

        long nextDelayMillis = calculateNextDelayMillis(attempt);
        enqueueRetry(collection, transactionNo, callbackUrl, body, attempt + 1, maxAttempts, nextDelayMillis);
    }

    private AttemptResult doSingleHttpAttempt(String callbackUrl, Map<String, Object> body) {
        String domain = extractDomain(callbackUrl);
        if (isCircuitOpen(domain)) {
            log.warn("merchant notify blocked by circuit breaker, domain={}", domain);
            return AttemptResult.failed("circuit_open");
        }
        try {
            // Notify merchant with JSON payload.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body == null ? new HashMap<>() : body, headers);
            PaymentHttpResponse response = restTemplateUtil.request(entity, HttpMethod.POST, callbackUrl);
            boolean success = response != null && Integer.valueOf(200).equals(response.getCode());
            if (success) {
                onCircuitSuccess(domain);
                log.info("merchant notify single attempt success, domain={}, callbackUrl={}", domain, callbackUrl);
                return AttemptResult.success();
            }
            onCircuitFailure(domain);
            log.warn("merchant notify single attempt failed, domain={}, callbackUrl={}, reason=response_code_not_200",
                    domain, callbackUrl);
            return AttemptResult.failed("response_code_not_200");
        } catch (Exception e) {
            onCircuitFailure(domain);
            log.warn("merchant notify single attempt exception, domain={}, callbackUrl={}, message={}",
                    domain, callbackUrl, e.getMessage());
            return AttemptResult.failed(e.getMessage() == null ? "http_request_error" : e.getMessage());
        }
    }

    private void enqueueRetry(
            boolean collection,
            String transactionNo,
            String callbackUrl,
            Map<String, Object> body,
            int attempt,
            int maxAttempts,
            long delayMillis) {
        try {
            MerchantNotifyRetryMessage message = new MerchantNotifyRetryMessage();
            message.setTransactionNo(transactionNo);
            message.setCallbackUrl(callbackUrl);
            message.setBody(body);
            message.setAttempt(attempt);
            message.setMaxAttempts(maxAttempts);

            String routingKey = collection
                    ? RabbitConfig.MERCHANT_NOTIFY_COLLECTION_QUEUE
                    : RabbitConfig.MERCHANT_NOTIFY_PAYING_QUEUE;
            sendDmqMessage.sendToDelayQueue(routingKey, JSON.toJSONString(message), delayMillis);
            log.info("merchant notify retry enqueued, type={}, transactionNo={}, attempt={}, maxAttempts={}, delayMs={}",
                    collection ? "collection" : "payout", transactionNo, attempt, maxAttempts, delayMillis);
        } catch (Exception e) {
            log.error("merchant notify retry enqueue failed, type={}, transactionNo={}, attempt={}, message={}",
                    collection ? "collection" : "payout", transactionNo, attempt, e.getMessage());
        }
    }

    private void finalizeCallback(
            boolean collection,
            String transactionNo,
            Map<String, Object> body,
            int totalAttempts,
            boolean success,
            String reason) {
        long now = System.currentTimeMillis() / 1000;
        int callbackStatus = success ? 2 : 1;
        long[] range = resolveTransactionNoTimeRange(transactionNo);
        int updated;
        if (collection) {
            // Conditional update avoids extra "finalized check" query and prevents duplicate finalization.
            updated = collectionOrderMapper.increaseCallbackTimesWhenPending(
                    transactionNo,
                    now,
                    totalAttempts,
                    callbackStatus,
                    success ? now : null,
                    range[0],
                    range[1]);
        } else {
            // Conditional update avoids extra "finalized check" query and prevents duplicate finalization.
            updated = payOrderMapper.increaseCallbackTimesWhenPending(
                    transactionNo,
                    now,
                    totalAttempts,
                    callbackStatus,
                    success ? now : null,
                    range[0],
                    range[1]);
        }

        if (updated <= 0) {
            log.info("merchant notify finalize skipped, already finalized or missing order, type={}, transactionNo={}",
                    collection ? "collection" : "payout",
                    transactionNo);
            return;
        }

        if (collection) {
            logMerchantNotifyFlowBatch(transactionNo, true, success, totalAttempts, reason, body);
        } else {
            logMerchantNotifyFlowBatch(transactionNo, false, success, totalAttempts, reason, body);
        }
        log.info("merchant notify finalized, type={}, transactionNo={}, success={}, attempts={}, reason={}",
                collection ? "collection" : "payout", transactionNo, success, totalAttempts, reason);
    }

    /**
     * Write merchant notify flow request/response in one service call.
     */
    private void logMerchantNotifyFlowBatch(
            String transactionNo,
            boolean collection,
            boolean success,
            int totalAttempts,
            String reason,
            Map<String, Object> requestBody) {
        List<OrderFlowLogEvent> events = new ArrayList<>(2);
        events.add(new OrderFlowLogEvent(OrderFlowStepEnum.MERCHANT_NOTIFY_REQUEST, true, requestBody));
        events.add(new OrderFlowLogEvent(
                OrderFlowStepEnum.MERCHANT_NOTIFY_RESPONSE,
                success,
                buildFinalResponsePayload(success, totalAttempts, reason)));
        orderFlowLogService.logBatch(transactionNo, collection, events);
    }

    private Map<String, Object> buildFinalResponsePayload(boolean success, int attempts, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", success);
        payload.put("attempts", attempts);
        payload.put("reason", reason);
        return payload;
    }

    private int resolveCallbackRetryTimes(String transactionNo) {
        boolean payout = CommonUtil.isPayoutTransactionNo(transactionNo);
        SystemConfigItemKeyEnum retryKey = payout
                ? SystemConfigItemKeyEnum.PAYOUT_CALLBACK_RETRY_TIMES
                : SystemConfigItemKeyEnum.COLLECTION_CALLBACK_RETRY_TIMES;
        Integer retryTimes = systemConfigGroupService.getConfigValue(
                retryKey, Integer.class, DEFAULT_CALLBACK_RETRY_TIMES);
        log.info("merchant notify retry config resolved, transactionNo={}, payout={}, retryTimes={}",
                transactionNo, payout, retryTimes);
        return retryTimes == null || retryTimes <= 0 ? DEFAULT_CALLBACK_RETRY_TIMES : retryTimes;
    }

    private long[] resolveTransactionNoTimeRange(String transactionNo) {
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        if (range == null) {
            return new long[]{0L, Long.MAX_VALUE};
        }
        return range;
    }

    private long calculateNextDelayMillis(int currentAttempt) {
        // Exponential backoff + small jitter to reduce retry bursts.
        long base = RETRY_BASE_DELAY_MS * (1L << Math.max(currentAttempt - 1, 0));
        long jitter = ThreadLocalRandom.current().nextLong(0L, 201L);
        return Math.min(base, RETRY_MAX_DELAY_MS) + jitter;
    }

    private String extractDomain(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(callbackUrl.trim());
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidHttpUrl(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(callbackUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return host != null
                    && !host.isBlank()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCircuitOpen(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(CB_OPEN_PREFIX + domain);
            return value != null && !value.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private void onCircuitFailure(String domain) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        try {
            String key = CB_FAIL_PREFIX + domain;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, CB_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count >= CB_FAIL_THRESHOLD) {
                stringRedisTemplate.opsForValue().set(CB_OPEN_PREFIX + domain, "1", CB_OPEN_SECONDS, TimeUnit.SECONDS);
                log.warn("merchant notify circuit opened, domain={}, failCount={}, openSeconds={}",
                        domain, count, CB_OPEN_SECONDS);
            }
        } catch (Exception ignored) {
        }
    }

    private void onCircuitSuccess(String domain) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(CB_OPEN_PREFIX + domain);
            stringRedisTemplate.delete(CB_FAIL_PREFIX + domain);
            log.info("merchant notify circuit reset by success, domain={}", domain);
        } catch (Exception ignored) {
        }
    }

    private static class AttemptResult {
        private final boolean success;
        private final String message;

        private AttemptResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static AttemptResult success() {
            return new AttemptResult(true, "ok");
        }

        private static AttemptResult failed(String message) {
            return new AttemptResult(false, message);
        }
    }
}
