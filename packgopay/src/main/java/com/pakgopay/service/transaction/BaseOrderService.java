package com.pakgopay.service.transaction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.data.reqeust.transaction.OrderQueryRequest;
import com.pakgopay.data.response.http.PaymentHttpResponse;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.PaymentMapper;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.BalanceService;
import com.pakgopay.timer.ReportTask;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseOrderService {
    private static final String API_KEY_PREFIX = "api-key ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @Autowired
    private ReportTask reportTask;

    @Autowired
    protected PaymentMapper paymentMapper;

    @Autowired
    protected MerchantInfoMapper merchantInfoMapper;

    // ----------------------------- status / notify parse -----------------------------

    protected TransactionStatus resolveNotifyStatus(String status) throws PakGoPayException {
        if (status == null || status.isBlank()) {
            log.warn("resolveNotifyStatus failed: status is empty");
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "status is empty");
        }
        if (TransactionStatus.SUCCESS.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.SUCCESS;
        }
        if (TransactionStatus.FAILED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.FAILED;
        }
        if (TransactionStatus.PROCESSING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PROCESSING;
        }
        if (TransactionStatus.PENDING.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.PENDING;
        }
        if (TransactionStatus.EXPIRED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.EXPIRED;
        }
        if (TransactionStatus.CANCELLED.getMessage().equalsIgnoreCase(status)) {
            return TransactionStatus.CANCELLED;
        }
        log.warn("resolveNotifyStatus failed: unsupported status={}", status);
        throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "unsupported status");
    }

    protected BigDecimal resolveOrderAmount(BigDecimal actualAmount, BigDecimal amount) {
        return actualAmount != null ? actualAmount : amount;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractHandlerData(Object handlerResponse) {
        if (handlerResponse == null) {
            return null;
        }
        if (handlerResponse instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (handlerResponse instanceof PaymentHttpResponse resp) {
            Object data = resp.getData();
            if (data instanceof Map<?, ?> dataMap) {
                return (Map<String, Object>) dataMap;
            }
        }
        return null;
    }

    protected void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    protected String extractTransactionNo(Map<String, Object> notifyData) {
        return extractDataField(notifyData, "order_no");
    }

    protected String extractMerchantNo(Map<String, Object> notifyData) {
        return extractDataField(notifyData, "mid");
    }

    private String extractDataField(Map<String, Object> notifyData, String key) {
        if (notifyData == null || key == null) {
            return null;
        }
        Object value = notifyData.get(key);
        if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }

    protected OrderQueryEntity buildOrderQueryEntity(OrderQueryRequest request) {
        OrderQueryEntity entity = new OrderQueryEntity();
        entity.setMerchantUserId(request.getMerchantUserId());
        entity.setTransactionNo(request.getTransactionNo());
        entity.setMerchantOrderNo(request.getMerchantOrderNo());
        entity.setCurrencyType(request.getCurrency());
        entity.setOrderStatus(request.getOrderStatus());
        entity.setOrderType(request.getOrderType());
        entity.setAmount(request.getAmount());
        entity.setChannelId(request.getChannelId());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPageNo(request.getPageNo());
        entity.setPageSize(request.getPageSize());
        return entity;
    }

    // ----------------------------- auth / sign -----------------------------

    protected PaymentDto fetchPaymentById(Long paymentId) {
        if (paymentId == null) {
            log.warn("fetchPaymentById failed: paymentId is null");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "paymentId is null");
        }
        PaymentDto paymentDto = paymentMapper.findByPaymentId(paymentId);
        if (paymentDto == null) {
            log.warn("fetchPaymentById failed: payment not found, paymentId={}", paymentId);
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    "payment not found, paymentId=" + paymentId);
        }
        return paymentDto;
    }

    protected MerchantInfoDto validateApiKeyAndMerchant(String merchantId, String authorization) {
        if (merchantId == null || merchantId.isBlank()) {
            log.warn("validateApiKeyAndMerchant failed: merchantId is empty");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "merchantId is empty");
        }
        String apiKey = extractApiKey(authorization);
        MerchantInfoDto merchantInfoDto = merchantInfoMapper.findByApiKey(apiKey);
        if (merchantInfoDto == null) {
            log.warn(
                    "validateApiKeyAndMerchant failed: apiKey not found, merchantId={}",
                    merchantId);
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "apiKey is invalid");
        }
        if (!merchantId.equals(merchantInfoDto.getUserId())) {
            log.warn(
                    "validateApiKeyAndMerchant failed: merchantId mismatch, merchantId={}, ownerUserId={}",
                    merchantId, merchantInfoDto.getUserId());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "merchantId does not match apiKey");
        }
        return merchantInfoDto;
    }

    /**
     * Verify request signature with merchant sign key.
     */
    protected void validateRequestSign(
            Map<String, Object> payload,
            String sign,
            MerchantInfoDto merchantInfoDto,
            String scene) {
        if (merchantInfoDto == null) {
            log.warn("validateRequestSign failed: merchantInfoDto is null, scene={}", scene);
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "merchant is invalid");
        }
        if (sign == null || sign.isBlank()) {
            log.warn("validateRequestSign failed: sign is empty, scene={}, merchantId={}",
                    scene, merchantInfoDto.getUserId());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "sign is empty");
        }
        String signKey = resolveMerchantSignKey(
                merchantInfoDto.getSignKey(),
                merchantInfoDto.getUserId());
        String expected = CryptoUtil.signHmacSha1Base64(payload, signKey);
        if (expected == null || !expected.equals(sign)) {
            log.warn("validateRequestSign failed: sign mismatch, scene={}, merchantId={}",
                    scene, merchantInfoDto.getUserId());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "sign is invalid");
        }
        log.info("validateRequestSign success, scene={}, merchantId={}",
                scene, merchantInfoDto.getUserId());
    }

    /**
     * Resolve merchant sign key from storage (supports encrypted and plain text key).
     */
    protected String resolveMerchantSignKey(String storedSignKey, String merchantId) {
        if (storedSignKey == null || storedSignKey.isBlank()) {
            log.warn("resolveMerchantSignKey failed: signKey is empty, merchantId={}", merchantId);
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "merchant signKey is empty");
        }
        if (!storedSignKey.startsWith("gcm:v1:")) {
            return storedSignKey;
        }
        try {
            return CryptoUtil.decryptSignKey(storedSignKey);
        } catch (Exception e) {
            log.warn("resolveMerchantSignKey failed: decrypt signKey error, merchantId={}, message={}",
                    merchantId, e.getMessage());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "merchant signKey is invalid");
        }
    }

    /**
     * Build signature payload by converting request object to map and extracting whitelist keys.
     */
    protected Map<String, Object> buildSignPayload(Object request, String... includeKeys) {
        Map<String, Object> source = OBJECT_MAPPER.convertValue(request, MAP_TYPE);
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String key : includeKeys) {
            payload.put(key, source.get(key));
        }
        return payload;
    }

    protected String extractApiKey(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            log.warn("extractApiKey failed: Authorization is empty");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "Authorization is empty");
        }
        String header = authorization.trim();
        if (header.regionMatches(true, 0, API_KEY_PREFIX, 0, API_KEY_PREFIX.length())) {
            String apiKey = header.substring(API_KEY_PREFIX.length()).trim();
            if (apiKey.isEmpty()) {
                log.warn("extractApiKey failed: apiKey is empty after prefix, header={}", authorization);
                throw new PakGoPayException(ResultCode.INVALID_PARAMS, "apiKey is empty");
            }
            return apiKey;
        }
        return header;
    }

    // ----------------------------- notify body / balance / report -----------------------------

    protected Map<String, Object> buildCollectionNotifyBody(
            CollectionOrderDto orderDto, TransactionStatus targetStatus, String key) {
        Map<String, Object> body = buildSignPayload(orderDto,
                "merchantUserId", "transactionNo", "merchantOrderNo", "createTime");
        body.put("amount", formatNotifyAmount(orderDto.getAmount()));
        body.put(
                "actualAmount",
                formatNotifyAmount(resolveOrderAmount(orderDto.getActualAmount(), orderDto.getAmount())));
        body.put("merchantFee", formatNotifyAmount(orderDto.getMerchantFee()));
        body.put("depositTime", orderDto.getSuccessCallbackTime());
        body.put("notifyTime", System.currentTimeMillis() / 1000);
        body.put("status", targetStatus.getMessage());
        body.put("origAmount", formatNotifyAmount(orderDto.getAmount()));
        body.put("payerName", null);
        body.put("sign", CryptoUtil.signHmacSha1Base64(body, key));
        return body;
    }

    protected Map<String, Object> buildPayNotifyBody(
            PayOrderDto orderDto, TransactionStatus targetStatus, String key) {
        Map<String, Object> body = buildSignPayload(orderDto,
                "merchantUserId", "transactionNo", "merchantOrderNo", "createTime");
        body.put("amount", formatNotifyAmount(orderDto.getAmount()));
        body.put(
                "actualAmount",
                formatNotifyAmount(resolveOrderAmount(orderDto.getActualAmount(), orderDto.getAmount())));
        body.put("merchantFee", formatNotifyAmount(orderDto.getMerchantFee()));
        body.put("depositTime", orderDto.getSuccessCallbackTime());
        body.put("notifyTime", System.currentTimeMillis() / 1000);
        body.put("status", targetStatus.getMessage());
        body.put("origAmount", formatNotifyAmount(orderDto.getAmount()));
        body.put("fromCardNo", null);
        body.put("sign", CryptoUtil.signHmacSha1Base64(body, key));
        return body;
    }

    protected String formatNotifyAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    protected void updateAgentFeeBalance(BalanceService balanceService,
                                         MerchantInfoDto merchantInfo,
                                         String currency,
                                         BigDecimal agent1Fee,
                                         BigDecimal agent2Fee,
                                         BigDecimal agent3Fee) {
        if (balanceService == null || merchantInfo == null
                || merchantInfo.getAgentInfos() == null || merchantInfo.getAgentInfos().isEmpty()) {
            return;
        }
        applyAgentFee(balanceService, merchantInfo.getAgentInfos(), CommonConstant.AGENT_LEVEL_FIRST, agent1Fee, currency);
        applyAgentFee(balanceService, merchantInfo.getAgentInfos(), CommonConstant.AGENT_LEVEL_SECOND, agent2Fee, currency);
        applyAgentFee(balanceService, merchantInfo.getAgentInfos(), CommonConstant.AGENT_LEVEL_THIRD, agent3Fee, currency);
    }

    protected void applyAgentFee(BalanceService balanceService,
                                 List<AgentInfoDto> agentInfos,
                                 Integer targetLevel,
                                 BigDecimal fee,
                                 String currency) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= CommonConstant.ZERO) {
            return;
        }
        for (AgentInfoDto agent : agentInfos) {
            if (agent != null && targetLevel.equals(agent.getLevel())) {
                CommonUtil.withBalanceLogContext("agent.fee", null, () -> {
                    balanceService.creditBalance(agent.getUserId(), currency, fee);
                });
                log.info("agent fee credited, agentUserId={}, level={}, amount={}",
                        agent.getUserId(), targetLevel, fee);
                return;
            }
        }
    }

    protected void refreshReportData(long recordDateEpoch, String currency) {
        try {
            if (currency != null && !currency.isBlank()) {
                ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
                LocalDate targetDate = Instant.ofEpochSecond(recordDateEpoch).atZone(zoneId).toLocalDate();
                LocalDate today = Instant.now().atZone(zoneId).toLocalDate();
                if (targetDate.isEqual(today)) {
                    log.info("refreshReportData skipped for today, recordDateEpoch={}, currency={}, targetDate={}",
                            recordDateEpoch, currency, targetDate);
                    return;
                }
            }
            reportTask.refreshReportsByEpoch(recordDateEpoch, currency);
        } catch (Exception e) {
            log.error("refreshReportData failed, error message: {}", e.getMessage());
        }
    }
}
