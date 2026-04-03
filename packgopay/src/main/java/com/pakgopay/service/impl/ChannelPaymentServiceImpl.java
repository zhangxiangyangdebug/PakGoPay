package com.pakgopay.service.impl;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.entity.channel.ChannelEntity;
import com.pakgopay.data.entity.channel.PaymentEntity;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.channel.ChannelResponse;
import com.pakgopay.data.response.channel.PaymentResponse;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChannelPaymentServiceImpl implements ChannelPaymentService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ChannelDto>> CHANNEL_LIST_TYPE = new TypeReference<>() {};
    private static final Set<String> INTERFACE_PARAM_MASK_KEYWORDS = Set.of("key");
    private static final String CHANNEL_PAYMENT_IDS_CACHE_PREFIX = "channel:paymentIds:byChannelIds:";
    private static final String CHANNEL_PAYMENT_IDS_CACHE_VERSION_KEY = "channel:paymentIds:cache:version";
    private static final int CHANNEL_PAYMENT_IDS_CACHE_TTL_SECONDS = 24 * 60 * 60;
    private static final int CHANNEL_PAYMENT_IDS_CACHE_JITTER_SECONDS = 60 * 60;
    private static final String ENABLE_PAYMENT_INFO_CACHE_PREFIX = "payment:enableInfo:byNoAndIds:";
    private static final String ENABLE_PAYMENT_INFO_CACHE_VERSION_KEY = "payment:enableInfo:cache:version";
    private static final int ENABLE_PAYMENT_INFO_CACHE_TTL_SECONDS = 24 * 60 * 60;
    private static final int ENABLE_PAYMENT_INFO_CACHE_JITTER_SECONDS = 60 * 60;
    private static final String AGENT_BY_USER_ID_CACHE_PREFIX = "agent:byUserId:";
    private static final String AGENT_CACHE_VERSION_KEY = "agent:chain:cache:version";
    private static final int AGENT_BY_USER_ID_CACHE_TTL_SECONDS = 24 * 60 * 60;
    private static final int AGENT_BY_USER_ID_CACHE_JITTER_SECONDS = 60 * 60;
    private static final String COUNTER_CHANNEL_HASH_KEY_PREFIX = "counter:{v1}:channel:";
    private static final String COUNTER_PAYMENT_HASH_KEY_PREFIX = "counter:{v1}:payment:";
    private static final String COUNTER_CHANNEL_ACTIVE_SET_KEY = "counter:{v1}:channel:active";
    private static final String COUNTER_PAYMENT_ACTIVE_SET_KEY = "counter:{v1}:payment:active";
    private static final int COUNTER_ACTIVE_SET_TTL_SECONDS = 3 * 24 * 60 * 60;
    private static final String COUNTER_FIELD_TOTAL = "total";
    private static final String COUNTER_FIELD_FAIL = "fail";
    private static final String COUNTER_FIELD_ORDER = "order";
    private static final String COUNTER_FIELD_SUCCESS = "success";
    private static final String COLLECTION_LIMIT_DAY_KEY_PREFIX = "collection:limit:{v1}:day:";
    private static final String COLLECTION_LIMIT_MONTH_KEY_PREFIX = "collection:limit:{v1}:month:";
    private static final String COLLECTION_LIMIT_TX_GUARD_KEY_PREFIX = "collection:limit:{v1}:tx:";
    private static final String COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY = "collection:limit:{v1}:activePayments";
    private static final int COLLECTION_LIMIT_TX_GUARD_TTL_SECONDS = 40 * 24 * 60 * 60;
    private static final int COLLECTION_LIMIT_DAY_KEY_GRACE_SECONDS = 2 * 60 * 60;
    private static final int COLLECTION_LIMIT_MONTH_KEY_GRACE_SECONDS = 2 * 60 * 60;
    private static final BigDecimal AMOUNT_SCALE = new BigDecimal("100");
    private static final RedisScript<List> COLLECTION_LIMIT_INCREMENT_SCRIPT = buildCollectionLimitIncrementScript();

    @Value("${biz.collection.limit.redis-enabled:true}")
    private boolean collectionLimitRedisEnabled;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // =====================
    // Payment selection
    // =====================
    /**
     * get available payment ids
     *
     * @param supportType     orderType (Collection / Payout)
     * @param transactionInfo transaction info
     * @return payment ids
     * @throws PakGoPayException Business Exception
     */
    @Override
    public Long selectPaymentId(Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException {
        long totalStart = System.currentTimeMillis();
        long stepStart = totalStart;
        log.info("selectPaymentId start, get available payment id");
        String resolvedChannelIds = resolveChannelIdsForMerchant(transactionInfo);
        log.info("selectPaymentId resolvedChannelIds, supportType={}, channelIds={}",
                CommonUtil.resolveSupportTypeLabel(supportType),
                resolvedChannelIds);

        // key:payment_id value:channel_id
        Map<Long, ChannelDto> paymentMap = new HashMap<>();
        // 1. get payment info list through channel ids and payment no
        stepStart = System.currentTimeMillis();
        List<PaymentDto> paymentDtoList = loadPaymentsByChannelIds(
                transactionInfo.getPaymentNo(), resolvedChannelIds, supportType, paymentMap);
        log.info("selectPaymentId loaded payments, size={}", paymentDtoList.size());

        // 2. filter support currency payment
        stepStart = System.currentTimeMillis();
        paymentDtoList = filterPaymentsByCurrency(paymentDtoList, transactionInfo.getCurrency());
        log.info("selectPaymentId currency filtered, currency={}, size={}", transactionInfo.getCurrency(), paymentDtoList.size());

        // 3. filter no limit payment infos
        stepStart = System.currentTimeMillis();
        paymentDtoList = filterPaymentsByLimits(transactionInfo.getAmount(), paymentDtoList, supportType);
        log.info("selectPaymentId limit filtered, amount={}, size={}", transactionInfo.getAmount(), paymentDtoList.size());

        stepStart = System.currentTimeMillis();
        PaymentDto paymentDto = selectPaymentByPerformance(paymentDtoList, supportType);
        log.info(
                "merchant payment search success, paymentId={}, paymentName={}",
                paymentDto.getPaymentId(),
                paymentDto.getPaymentName());

        // payment info
        transactionInfo.setPaymentId(paymentDto.getPaymentId());
        transactionInfo.setPaymentInfo(paymentDto);
        // channel info
        transactionInfo.setChannelId(paymentMap.get(paymentDto.getPaymentId()).getChannelId());
        transactionInfo.setChannelInfo(paymentMap.get(paymentDto.getPaymentId()));

        log.info("selectPaymentId success, id: {}", paymentDto.getPaymentId());
        return paymentDto.getPaymentId();
    }

    // =====================
    // Stats updates
    // =====================
    @Override
    public void updateChannelAndPaymentCounters(CollectionOrderDto order, TransactionStatus status) {
        // Update counters only for final status changes.
        boolean isSuccess = TransactionStatus.SUCCESS.equals(status);
        boolean isFailure = TransactionStatus.FAILED.equals(status)
                || TransactionStatus.EXPIRED.equals(status);
        if (!isSuccess && !isFailure) {
            log.info("skip stats update, status={}", status == null ? null : status.getCode());
            return;
        }

        Long channelId = order.getChannelId();
        if (channelId != null) {
            long failInc = isFailure ? 1L : 0L;
            accumulateCounter(
                    COUNTER_CHANNEL_HASH_KEY_PREFIX + channelId,
                    COUNTER_CHANNEL_ACTIVE_SET_KEY,
                    COUNTER_FIELD_TOTAL,
                    1L,
                    COUNTER_FIELD_FAIL,
                    failInc);
            log.info("channel stats accumulated, channelId={}, failInc={}", channelId, failInc);
        }

        Long paymentId = order.getPaymentId();
        if (paymentId != null) {
            long successInc = isSuccess ? 1L : 0L;
            accumulateCounter(
                    COUNTER_PAYMENT_HASH_KEY_PREFIX + paymentId,
                    COUNTER_PAYMENT_ACTIVE_SET_KEY,
                    COUNTER_FIELD_ORDER,
                    1L,
                    COUNTER_FIELD_SUCCESS,
                    successInc);
            log.info("payment stats accumulated, paymentId={}, successInc={}", paymentId, successInc);
        }
    }

    @Override
    public void flushCounterDeltas() {
        long now = System.currentTimeMillis() / 1000;
        flushChannelCounterDeltas(now);
        flushPaymentCounterDeltas(now);
    }

    private void flushChannelCounterDeltas(long now) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(COUNTER_CHANNEL_ACTIVE_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            Long channelId = safeParseLong(id);
            if (channelId == null) {
                stringRedisTemplate.opsForSet().remove(COUNTER_CHANNEL_ACTIVE_SET_KEY, id);
                continue;
            }
            String hashKey = COUNTER_CHANNEL_HASH_KEY_PREFIX + channelId;
            long totalInc = readHashLong(hashKey, COUNTER_FIELD_TOTAL);
            long failInc = readHashLong(hashKey, COUNTER_FIELD_FAIL);
            if (totalInc <= 0 && failInc <= 0) {
                clearCounterHashIfEmpty(hashKey, COUNTER_FIELD_TOTAL, COUNTER_FIELD_FAIL);
                stringRedisTemplate.opsForSet().remove(COUNTER_CHANNEL_ACTIVE_SET_KEY, id);
                continue;
            }
            try {
                int updated = channelMapper.increaseCountersByChannelIdDelta(channelId, totalInc, failInc, now);
                if (updated > 0) {
                    settleCounterDelta(hashKey, COUNTER_FIELD_TOTAL, totalInc, COUNTER_FIELD_FAIL, failInc);
                    if (readHashLong(hashKey, COUNTER_FIELD_TOTAL) <= 0 && readHashLong(hashKey, COUNTER_FIELD_FAIL) <= 0) {
                        stringRedisTemplate.delete(hashKey);
                        stringRedisTemplate.opsForSet().remove(COUNTER_CHANNEL_ACTIVE_SET_KEY, id);
                    }
                } else {
                    log.warn("flush channel counter delta skipped, channelId={}", channelId);
                }
            } catch (Exception e) {
                log.warn("flush channel counter delta failed, channelId={}, message={}", channelId, e.getMessage());
            }
        }
    }

    private void flushPaymentCounterDeltas(long now) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(COUNTER_PAYMENT_ACTIVE_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            Long paymentId = safeParseLong(id);
            if (paymentId == null) {
                stringRedisTemplate.opsForSet().remove(COUNTER_PAYMENT_ACTIVE_SET_KEY, id);
                continue;
            }
            String hashKey = COUNTER_PAYMENT_HASH_KEY_PREFIX + paymentId;
            long orderInc = readHashLong(hashKey, COUNTER_FIELD_ORDER);
            long successInc = readHashLong(hashKey, COUNTER_FIELD_SUCCESS);
            if (orderInc <= 0 && successInc <= 0) {
                clearCounterHashIfEmpty(hashKey, COUNTER_FIELD_ORDER, COUNTER_FIELD_SUCCESS);
                stringRedisTemplate.opsForSet().remove(COUNTER_PAYMENT_ACTIVE_SET_KEY, id);
                continue;
            }
            try {
                int updated = paymentMapper.increaseCountersByPaymentIdDelta(paymentId, orderInc, successInc, now);
                if (updated > 0) {
                    settleCounterDelta(hashKey, COUNTER_FIELD_ORDER, orderInc, COUNTER_FIELD_SUCCESS, successInc);
                    if (readHashLong(hashKey, COUNTER_FIELD_ORDER) <= 0 && readHashLong(hashKey, COUNTER_FIELD_SUCCESS) <= 0) {
                        stringRedisTemplate.delete(hashKey);
                        stringRedisTemplate.opsForSet().remove(COUNTER_PAYMENT_ACTIVE_SET_KEY, id);
                    }
                } else {
                    log.warn("flush payment counter delta skipped, paymentId={}", paymentId);
                }
            } catch (Exception e) {
                log.warn("flush payment counter delta failed, paymentId={}, message={}", paymentId, e.getMessage());
            }
        }
    }

    private void accumulateCounter(
            String hashKey,
            String activeSetKey,
            String field1,
            long delta1,
            String field2,
            long delta2) {
        if (delta1 != 0L) {
            stringRedisTemplate.opsForHash().increment(hashKey, field1, delta1);
        }
        if (delta2 != 0L) {
            stringRedisTemplate.opsForHash().increment(hashKey, field2, delta2);
        }
        stringRedisTemplate.expire(hashKey, COUNTER_ACTIVE_SET_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        String id = hashKey.substring(hashKey.lastIndexOf(':') + 1);
        stringRedisTemplate.opsForSet().add(activeSetKey, id);
        stringRedisTemplate.expire(activeSetKey, COUNTER_ACTIVE_SET_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private long readHashLong(String hashKey, String field) {
        Object raw = stringRedisTemplate.opsForHash().get(hashKey, field);
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception e) {
            return 0L;
        }
    }

    private void settleCounterDelta(
            String hashKey,
            String field1,
            long consumed1,
            String field2,
            long consumed2) {
        if (consumed1 > 0) {
            stringRedisTemplate.opsForHash().increment(hashKey, field1, -consumed1);
        }
        if (consumed2 > 0) {
            stringRedisTemplate.opsForHash().increment(hashKey, field2, -consumed2);
        }
    }

    private void clearCounterHashIfEmpty(String hashKey, String field1, String field2) {
        if (readHashLong(hashKey, field1) <= 0 && readHashLong(hashKey, field2) <= 0) {
            stringRedisTemplate.delete(hashKey);
        }
    }

    // =====================
    // Fee calculation
    // =====================
    @Override
    public void calculateTransactionFees(TransactionInfo transactionInfo, OrderType orderType) {
        BigDecimal amount = transactionInfo.getActualAmount() != null
                ? transactionInfo.getActualAmount()
                : transactionInfo.getAmount();
        // merchant fee
        BigDecimal fixedFee = null;
        BigDecimal rate = null;
        // get fixedFee and rate
        if (orderType.equals(OrderType.PAY_OUT_ORDER)) {
            fixedFee = transactionInfo.getMerchantInfo().getPayFixedFee();
            rate = transactionInfo.getMerchantInfo().getPayRate();
        } else {
            fixedFee = transactionInfo.getMerchantInfo().getCollectionFixedFee();
            rate = transactionInfo.getMerchantInfo().getCollectionRate();
        }

        calculateAgentFees(transactionInfo, orderType);

        CalcUtil.FeeCalcInput feeInput = new CalcUtil.FeeCalcInput();
        feeInput.amount = amount;
        feeInput.merchantRate = rate;
        feeInput.merchantFixed = fixedFee;
        feeInput.agent1Rate = transactionInfo.getAgent1Rate();
        feeInput.agent1Fixed = transactionInfo.getAgent1FixedFee();
        feeInput.agent2Rate = transactionInfo.getAgent2Rate();
        feeInput.agent2Fixed = transactionInfo.getAgent2FixedFee();
        feeInput.agent3Rate = transactionInfo.getAgent3Rate();
        feeInput.agent3Fixed = transactionInfo.getAgent3FixedFee();

        CalcUtil.FeeProfitResult feeProfit = CalcUtil.calculateTierProfits(feeInput);

        BigDecimal merchantFee = CalcUtil.defaultBigDecimal(feeProfit.merchantFee);
        if (amount != null && amount.compareTo(merchantFee) <= 0) {
            log.error("amount not support merchant fee, actualAmount={}, merchantFee={}", amount, merchantFee);
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "amount not support merchant fee");
        }

        transactionInfo.setMerchantFee(merchantFee);
        transactionInfo.setAgent1Fee(feeProfit.agent1Profit);
        transactionInfo.setAgent2Fee(feeProfit.agent2Profit);
        transactionInfo.setAgent3Fee(feeProfit.agent3Profit);
        transactionInfo.setMerchantRate(rate);
        transactionInfo.setMerchantFixedFee(fixedFee);
    }

    private void calculateAgentFees(TransactionInfo transactionInfo, OrderType orderType) {
        String agentId = transactionInfo.getMerchantInfo().getParentId();
        if (agentId == null) {
            log.info("merchant has not agent");
            return;
        }

        List<AgentInfoDto> chains = new ArrayList<>();
        Set<String> visited = new HashSet<>(); // 防环

        AgentInfoDto current = loadAgentByUserId(agentId);
        if (current == null) {
            log.error("agent info is not exists, agentId {}", agentId);
            return;
        }

        Integer startLevel = current.getLevel();
        while (startLevel >= CommonConstant.AGENT_LEVEL_FIRST) {
            // preventing the formation of circular links
            if (current == null || visited.contains(current.getUserId())) {
                log.warn("userId is duplicate");
                break;
            } else {
                visited.add(current.getUserId());
            }
            // check agent enable status
            if (CommonConstant.ENABLE_STATUS_ENABLE.equals(current.getStatus())) {
                chains.add(current);
            } else {
                log.info("agent is not enable");
                break;
            }
            // check agent parent id
            if (!StringUtils.hasText(current.getParentId())) {
                log.info("agent has not parent agent");
                break;
            }
            // check agent level
            if (current.getLevel() != null && current.getLevel() <= 1) {
                log.info("agent level is {}", current.getLevel());
                break;
            }

            current = loadAgentByUserId(current.getParentId());
            startLevel--;
        }

        applyAgentFeesToTransaction(transactionInfo, orderType, chains);
    }

    /**
     * save agent's fee info
     *
     * @param transactionInfo transaction info
     * @param orderType       order type
     * @param chains          agent info list
     */
    private void applyAgentFeesToTransaction(TransactionInfo transactionInfo, OrderType orderType, List<AgentInfoDto> chains) {
        chains.forEach(info -> {

            BigDecimal rate = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayRate() : info.getCollectionRate();
            BigDecimal fixedFee = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayFixedFee() : info.getCollectionFixedFee();
            // First level agent
            if (CommonConstant.AGENT_LEVEL_FIRST.equals(info.getLevel())) {
                transactionInfo.setAgent1Rate(rate);
                transactionInfo.setAgent1FixedFee(fixedFee);
            }
            // Second level agent
            if (CommonConstant.AGENT_LEVEL_SECOND.equals(info.getLevel())) {
                transactionInfo.setAgent2Rate(rate);
                transactionInfo.setAgent2FixedFee(fixedFee);
            }
            // Third level agent
            if (CommonConstant.AGENT_LEVEL_THIRD.equals(info.getLevel())) {
                transactionInfo.setAgent3Rate(rate);
                transactionInfo.setAgent3FixedFee(fixedFee);
            }
        });
    }

    /**
     * get agent info by agent id
     *
     * @param agentId agent id
     * @return agent info
     */
    private AgentInfoDto loadAgentByUserId(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            return null;
        }
        String cacheKey = buildAgentByUserIdCacheKey(agentId);
        try {
            String cacheVal = redisUtil.getValue(cacheKey);
            if (StringUtils.hasText(cacheVal)) {
                return OBJECT_MAPPER.readValue(cacheVal, AgentInfoDto.class);
            }
        } catch (Exception e) {
            log.warn("loadAgentByUserId cache read failed, agentId={}, message={}", agentId, e.getMessage());
        }

        try {
            AgentInfoDto agentInfoDto = agentInfoMapper.findByUserId(agentId);
            if (agentInfoDto == null) {
                return null;
            }
            int ttl = AGENT_BY_USER_ID_CACHE_TTL_SECONDS + new Random().nextInt(AGENT_BY_USER_ID_CACHE_JITTER_SECONDS + 1);
            redisUtil.setWithSecondExpire(cacheKey, OBJECT_MAPPER.writeValueAsString(agentInfoDto), ttl);
            return agentInfoDto;
        } catch (Exception e) {
            log.error("agentInfoMapper findByUserId failed, agentId: {} message: {}", agentId, e.getMessage());
            return null;
        }
    }

    private String buildAgentByUserIdCacheKey(String agentId) {
        return AGENT_BY_USER_ID_CACHE_PREFIX + resolveAgentCacheVersion() + ":" + agentId;
    }

    private String resolveAgentCacheVersion() {
        try {
            String version = redisUtil.getValue(AGENT_CACHE_VERSION_KEY);
            return StringUtils.hasText(version) ? version : "1";
        } catch (Exception e) {
            log.warn("resolveAgentCacheVersion failed, message={}", e.getMessage());
            return "1";
        }
    }

    // =====================
    // Selection helpers
    // =====================
    private PaymentDto selectPaymentByPerformance(List<PaymentDto> paymentDtoList, Integer supportType) {
        if (paymentDtoList.size() == 1) {
            return paymentDtoList.getFirst();
        }
        Comparator<PaymentDto> comparator = Comparator
                .comparingDouble(this::computeSuccessRate)
                .thenComparingLong(dto -> CommonUtil.defaultLong(dto.getSuccessQuantity(), 0L))
                .thenComparing(dto -> parsePaymentRate(dto, supportType), Comparator.reverseOrder());
        PaymentDto best = paymentDtoList.stream()
                .max(comparator)
                .orElse(paymentDtoList.getFirst());
        List<PaymentDto> topCandidates = paymentDtoList.stream()
                .filter(dto -> comparator.compare(dto, best) == 0)
                .toList();
        if (topCandidates.size() == 1) {
            return topCandidates.getFirst();
        }
        PaymentDto selected = topCandidates.get(new Random().nextInt(topCandidates.size()));
        return selected;
    }

    private double computeSuccessRate(PaymentDto dto) {
        long total = CommonUtil.defaultLong(dto.getOrderQuantity(), 0L);
        if (total <= 0) {
            return 0.0d;
        }
        return (double) CommonUtil.defaultLong(dto.getSuccessQuantity(), 0L) / (double) total;
    }

    private BigDecimal parsePaymentRate(PaymentDto dto, Integer supportType) {
        String rate = CommonConstant.SUPPORT_TYPE_PAY.equals(supportType)
                ? dto.getPaymentPayRate()
                : dto.getPaymentCollectionRate();
        if (rate == null || rate.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rate.trim());
        } catch (NumberFormatException e) {
            log.warn("invalid payment rate: {}", rate);
            return BigDecimal.ZERO;
        }
    }

    private String resolveChannelIdsForMerchant(TransactionInfo transactionInfo) throws PakGoPayException {
        MerchantInfoDto merchantInfo = transactionInfo.getMerchantInfo();
        String resolvedChannelIds = merchantInfo == null ? null : merchantInfo.getChannelIds();
        if (StringUtils.hasText(resolvedChannelIds)) {
            return resolvedChannelIds;
        }

        String agentId = merchantInfo == null ? null : merchantInfo.getParentId();
        if (!StringUtils.hasText(agentId)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not agent channel");
        }

        AgentInfoDto agentInfo = merchantInfo.getCurrentAgentInfo();
        if (agentInfo == null || !StringUtils.hasText(agentInfo.getChannelIds())) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "agent has not channel");
        }

        log.info("merchant channelIds empty, use agent channelIds, agentId={}", agentId);
        return agentInfo.getChannelIds();
    }

    /**
     * get payment infos by merchant's channel and payment no
     *
     * @param paymentNo   payment no
     * @param channelIds  merchant's channel
     * @param supportType orderType (Collection / Payout)
     * @param paymentMap  payment map channel (key: payment id value: channel info)
     * @return paymentInfo
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> loadPaymentsByChannelIds(
            String paymentNo, String channelIds, Integer supportType, Map<Long, ChannelDto> paymentMap)
            throws PakGoPayException {
        long stepStart = System.currentTimeMillis();
        log.info("loadPaymentsByChannelIds start, paymentNo={}, supportType={}, channelIds={}",
                paymentNo, CommonUtil.resolveSupportTypeLabel(supportType), channelIds);
        // 1. obtain merchant's channel id list
        if (!StringUtils.hasText(channelIds)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        List<Long> channelIdList = CommonUtil.parseIds(channelIds);
        if (channelIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }
        log.info("channelIds parsed, channelCount={}", channelIdList);

        // 2. obtain merchant's available payment ids by channel ids
        stepStart = System.currentTimeMillis();
        Set<Long> paymentIdList = collectPaymentIdsByChannelIds(channelIdList, paymentMap);
        log.info("paymentIds resolved by channelIds, paymentCount={}", paymentIdList);

        // 3. obtain merchant's available payment infos by channel ids
        stepStart = System.currentTimeMillis();
        List<PaymentDto> paymentDtoList = loadEnablePaymentsByNoWithCache(supportType, paymentNo, paymentIdList);
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "Merchants have no available matching payments");
        }
        log.info("payments loaded, paymentCount={}", paymentDtoList.size());
        stepStart = System.currentTimeMillis();
        paymentDtoList = filterPaymentsByEnableTime(paymentDtoList);
        log.info("payments filtered by enable time, paymentCount={}", paymentDtoList.size());
        log.info("payments summary, supportType={}, channels={}, payments={}, currencies={}",
                CommonUtil.resolveSupportTypeLabel(supportType),
                buildChannelSummary(paymentDtoList, paymentMap),
                buildPaymentSummary(paymentDtoList),
                paymentDtoList.stream()
                        .map(PaymentDto::getCurrency)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        return paymentDtoList;
    }

    private List<String> buildChannelSummary(List<PaymentDto> paymentDtoList, Map<Long, ChannelDto> paymentMap) {
        Map<Long, String> channelNames = new LinkedHashMap<>();
        for (PaymentDto payment : CommonUtil.safeList(paymentDtoList)) {
            ChannelDto channel = paymentMap.get(payment.getPaymentId());
            if (channel == null || channel.getChannelId() == null) {
                continue;
            }
            channelNames.putIfAbsent(channel.getChannelId(), channel.getChannelName());
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : channelNames.entrySet()) {
            result.add(entry.getKey() + ":" + entry.getValue());
        }
        return result;
    }

    private List<String> buildPaymentSummary(List<PaymentDto> paymentDtoList) {
        List<String> result = new ArrayList<>();
        for (PaymentDto payment : CommonUtil.safeList(paymentDtoList)) {
            if (payment == null || payment.getPaymentId() == null) {
                continue;
            }
            result.add(payment.getPaymentId() + ":" + payment.getPaymentName());
        }
        return result;
    }


    private List<PaymentDto> filterPaymentsByEnableTime(List<PaymentDto> paymentDtoList) throws PakGoPayException {
        // Filter payments by enableTimePeriod (format: HH:mm:ss,HH:mm:ss).
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        List<PaymentDto> availablePayments = paymentDtoList.stream()
                .filter(payment -> {
                    boolean allowed = isWithinEnableTimeWindow(now, payment.getEnableTimePeriod());
                    if (!allowed) {
                        log.warn("payment filtered by enableTimePeriod, paymentId={}, reason={}",
                                payment.getPaymentId(),
                                resolveEnableTimeRejectReason(now, payment.getEnableTimePeriod()));
                    }
                    return allowed;
                })
                .toList();
        if (availablePayments.isEmpty()) {
            log.warn("no available payments in enable time period, now={}", now);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "no available payments in time period");
        }
        return availablePayments;
    }

    private String resolveEnableTimeRejectReason(LocalTime now, String enableTimePeriod) {
        if (!StringUtils.hasText(enableTimePeriod)) {
            return "missing enableTimePeriod";
        }
        String[] parts = enableTimePeriod.split(",");
        if (parts.length != 2) {
            return "invalid enableTimePeriod format";
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            if (start.equals(end)) {
                return "all-day enabled";
            }
            if (start.isBefore(end)) {
                return "outside same-day window";
            }
            return "outside cross-midnight window";
        } catch (Exception e) {
            return "parse enableTimePeriod failed";
        }
    }

    private boolean isWithinEnableTimeWindow(LocalTime now, String enableTimePeriod) {
        if (!StringUtils.hasText(enableTimePeriod)) {
            return true;
        }
        String[] parts = enableTimePeriod.split(",");
        if (parts.length != 2) {
            log.warn("invalid enableTimePeriod format: {}", enableTimePeriod);
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            if (start.equals(end)) {
                // Equal start/end means all-day enabled.
                return true;
            }
            if (start.isBefore(end)) {
                // Same-day window.
                return !now.isBefore(start) && !now.isAfter(end);
            }
            // Cross-midnight window
            return !now.isBefore(start) || !now.isAfter(end);
        } catch (Exception e) {
            log.warn("parse enableTimePeriod failed: {}", enableTimePeriod);
            return false;
        }
    }

    /**
     * filter payment that not support this order currency
     *
     * @param availablePayments available payments
     * @param currency          currency type (example:VND,PKR,USD)
     * @return filtered payments
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> filterPaymentsByCurrency(List<PaymentDto> availablePayments, String currency) throws PakGoPayException {
        availablePayments = availablePayments.stream()
                .filter(payment -> payment.getCurrency().equals(currency)).toList();
        if (availablePayments.isEmpty()) {
            log.error("availablePayments is empty");
            throw new PakGoPayException(ResultCode.PAYMENT_NOT_SUPPORT_CURRENCY, "channel is not support this currency: " + currency);
        }
        return availablePayments;
    }

    /**
     * filter payment that over limit daily/montly amount sum
     *
     * @param amount         order amount
     * @param paymentDtoList payment infos
     * @param supportType    orderType (Collection / Payout)
     * @return filtered payments
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> filterPaymentsByLimits(BigDecimal amount, List<PaymentDto> paymentDtoList, Integer supportType) throws PakGoPayException {
        long stepStart = System.currentTimeMillis();
        // Filter orders by amount that match the maximum and minimum range of the channel.
        paymentDtoList = paymentDtoList.stream().filter(dto ->
                        // check payment min amount
                {
                    boolean result = amount.compareTo(dto.getPaymentMinAmount()) >= 0
                            // check payment max amount
                            && amount.compareTo(dto.getPaymentMaxAmount()) <= 0;

                    if (!result) {
                        log.warn("paymentName: {}, amount max: {} min: {}, over limit amount: {}"
                                , dto.getPaymentName(), dto.getPaymentMaxAmount(), dto.getPaymentMinAmount(), amount);
                    }
                    return result;
                })
                .toList();
        if (paymentDtoList.isEmpty()) {
            log.error("paymentDtoList is empty, amount is over limit, amount: {}", amount);
            throw new PakGoPayException(ResultCode.ORDER_AMOUNT_OVER_LIMIT, "the amount over merchant's payment limit");
        }

        // get current day and month, used amount
        stepStart = System.currentTimeMillis();
        List<Long> enAblePaymentIds = paymentDtoList.stream().map(PaymentDto::getPaymentId).toList();
        Map<Long, BigDecimal> currentDayAmountSum = new HashMap<>();
        Map<Long, BigDecimal> currentMonthAmountSum = new HashMap<>();
        loadCurrentAmountSums(enAblePaymentIds, supportType, currentDayAmountSum, currentMonthAmountSum);

        // filter no limit payment
        stepStart = System.currentTimeMillis();
        List<PaymentDto> availablePayments = paymentDtoList.stream().filter(dto -> {
                    BigDecimal currentDaySum = CalcUtil.safeAdd(currentDayAmountSum.get(dto.getPaymentId()), amount);
                    BigDecimal currentMonthSum = CalcUtil.safeAdd(currentMonthAmountSum.get(dto.getPaymentId()), amount);
                    BigDecimal dailyLimit = CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)
                            ? dto.getCollectionDailyLimit()
                            : dto.getPayDailyLimit();
                    BigDecimal monthlyLimit = CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)
                            ? dto.getCollectionMonthlyLimit()
                            : dto.getPayDailyLimit();
                    boolean inDailyLimit = currentDaySum.compareTo(dailyLimit) <= 0;
                    boolean inMonthlyLimit = currentMonthSum.compareTo(monthlyLimit) <= 0;
                    boolean inMinAmount = amount.compareTo(dto.getPaymentMinAmount()) >= 0;
                    boolean inMaxAmount = amount.compareTo(dto.getPaymentMaxAmount()) <= 0;
                    boolean result = inDailyLimit && inMonthlyLimit && inMinAmount && inMaxAmount;
                    if (!result) {
                        log.warn("payment limit filtered, paymentId={}, dailySum={}, dailyLimit={}, monthlySum={}, monthlyLimit={}, minAmount={}, maxAmount={}, amount={}",
                                dto.getPaymentId(),
                                currentDaySum,
                                dailyLimit,
                                currentMonthSum,
                                monthlyLimit,
                                dto.getPaymentMinAmount(),
                                dto.getPaymentMaxAmount(),
                                amount);
                    }
                    return result;
                })
                .toList();
        if (availablePayments.isEmpty()) {
            throw new PakGoPayException(ResultCode.PAYMENT_AMOUNT_OVER_LIMIT, "Merchant‘s payments over daily/monthly limit");
        }
        return availablePayments;
    }

    /**
     * get current daily/monthly amount sum
     *
     * @param enAblePaymentIds      paymentId
     * @param supportType           orderType (Collection / Payout)
     * @param currentDayAmountSum   daily amount (key: paymentId value: amount sum)
     * @param currentMonthAmountSum monthly amount (key: paymentId value: amount sum)
     */
    private void loadCurrentAmountSums(
            List<Long> enAblePaymentIds, Integer supportType, Map<Long, BigDecimal> currentDayAmountSum,
            Map<Long, BigDecimal> currentMonthAmountSum) throws PakGoPayException {
        long stepStart = System.currentTimeMillis();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        ZonedDateTime dayStart = today.atStartOfDay(zoneId);
        long dayStartTime = dayStart.toEpochSecond();
        long nextDayStartTime = dayStart.plusDays(1).toEpochSecond();
        ZonedDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
        long monthStartTime = monthStart.toEpochSecond();
        long nextMonthStartTime = monthStart.plusMonths(1).toEpochSecond();

        List<PaymentAmountAggDto> amountAggList;
        if (CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)) {
            // Collection limit path: prefer Redis counters to reduce DB round-trips.
            if (collectionLimitRedisEnabled) {
                stepStart = System.currentTimeMillis();
                loadCollectionAmountSumsFromRedis(
                        enAblePaymentIds,
                        currentDayAmountSum,
                        currentMonthAmountSum,
                        dayStartTime,
                        nextDayStartTime,
                        monthStartTime,
                        nextMonthStartTime,
                        zoneId,
                        today);
                return;
            } else {
                try {
                    stepStart = System.currentTimeMillis();
                    amountAggList = collectionOrderMapper.sumCollectionAmountByPaymentIds(
                            enAblePaymentIds, monthStartTime, nextMonthStartTime, dayStartTime, nextDayStartTime);
                } catch (Exception e) {
                    log.error("collectionOrderMapper sumCollectionAmountByPaymentIds failed, message {}", e.getMessage());
                    throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
                }
            }
        } else {
            // order type Payout: aggregate in DB to avoid loading monthly details to JVM.
            try {
                stepStart = System.currentTimeMillis();
                amountAggList = payOrderMapper.sumPayAmountByPaymentIds(
                        enAblePaymentIds, monthStartTime, nextMonthStartTime, dayStartTime, nextDayStartTime);
            } catch (Exception e) {
                log.error("payOrderMapper sumPayAmountByPaymentIds failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }
        }

        if (amountAggList == null || amountAggList.isEmpty()) {
            return;
        }

        stepStart = System.currentTimeMillis();
        accumulateAmountSums(amountAggList, currentDayAmountSum, currentMonthAmountSum);

    }

    /**
     * Merge DB aggregated day/month payment sums into in-memory maps.
     */
    private void accumulateAmountSums(
            List<PaymentAmountAggDto> amountAggList,
            Map<Long, BigDecimal> currentDayAmountSum,
            Map<Long, BigDecimal> currentMonthAmountSum) {
        if (amountAggList == null || amountAggList.isEmpty()) {
            return;
        }
        for (PaymentAmountAggDto agg : amountAggList) {
            if (agg == null || agg.getPaymentId() == null) {
                continue;
            }
            currentDayAmountSum.put(
                    agg.getPaymentId(),
                    CalcUtil.defaultBigDecimal(agg.getDayAmount()));
            currentMonthAmountSum.put(
                    agg.getPaymentId(),
                    CalcUtil.defaultBigDecimal(agg.getMonthAmount()));
        }
    }

    @Override
    public void recordCollectionAmountUsage(Long paymentId, BigDecimal amount, String transactionNo, Long createTime) {
        if (!collectionLimitRedisEnabled) {
            return;
        }
        if (paymentId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(transactionNo) || createTime == null) {
            return;
        }
        try {
            String guardKey = COLLECTION_LIMIT_TX_GUARD_KEY_PREFIX + transactionNo;
            BigInteger amountCent = toCent(amount);
            LocalDate orderDate = Instant.ofEpochSecond(createTime).atZone(ZoneId.systemDefault()).toLocalDate();
            String dayKey = buildCollectionDayKey(paymentId, orderDate);
            String monthKey = buildCollectionMonthKey(paymentId, orderDate);

            boolean updated = executeCollectionLimitIncrementLua(
                    guardKey,
                    dayKey,
                    monthKey,
                    COLLECTION_LIMIT_TX_GUARD_TTL_SECONDS,
                    amountCent.toString(),
                    secondsUntilDayExpire(orderDate, ZoneId.systemDefault()),
                    secondsUntilMonthExpire(orderDate, ZoneId.systemDefault()));
            if (!updated) {
                return;
            }

            stringRedisTemplate.opsForSet().add(COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY, String.valueOf(paymentId));
            // Keep active set for reconcile worker.
            stringRedisTemplate.expire(COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY, COLLECTION_LIMIT_TX_GUARD_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("recordCollectionAmountUsage failed, paymentId={}, transactionNo={}, message={}",
                    paymentId, transactionNo, e.getMessage());
        }
    }

    @Override
    public void reconcileCollectionAmountUsage() {
        if (!collectionLimitRedisEnabled) {
            return;
        }
        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY);
            if (members == null || members.isEmpty()) {
                return;
            }
            List<Long> paymentIds = members.stream()
                    .filter(StringUtils::hasText)
                    .map(this::safeParseLong)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (paymentIds.isEmpty()) {
                return;
            }
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            long dayStartTime = today.atStartOfDay(zoneId).toEpochSecond();
            long nextDayStartTime = today.plusDays(1).atStartOfDay(zoneId).toEpochSecond();
            long monthStartTime = today.withDayOfMonth(1).atStartOfDay(zoneId).toEpochSecond();
            long nextMonthStartTime = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toEpochSecond();

            List<PaymentAmountAggDto> aggList = collectionOrderMapper.sumCollectionAmountByPaymentIds(
                    paymentIds, monthStartTime, nextMonthStartTime, dayStartTime, nextDayStartTime);
            Map<Long, PaymentAmountAggDto> aggMap = new HashMap<>();
            for (PaymentAmountAggDto dto : CommonUtil.safeList(aggList)) {
                if (dto != null && dto.getPaymentId() != null) {
                    aggMap.put(dto.getPaymentId(), dto);
                }
            }
            for (Long paymentId : paymentIds) {
                PaymentAmountAggDto dto = aggMap.get(paymentId);
                BigDecimal dayAmount = dto == null ? BigDecimal.ZERO : CalcUtil.defaultBigDecimal(dto.getDayAmount());
                BigDecimal monthAmount = dto == null ? BigDecimal.ZERO : CalcUtil.defaultBigDecimal(dto.getMonthAmount());
                String dayKey = buildCollectionDayKey(paymentId, today);
                String monthKey = buildCollectionMonthKey(paymentId, today);
                stringRedisTemplate.opsForValue().set(dayKey, toCent(dayAmount).toString(),
                        secondsUntilDayExpire(today, zoneId), java.util.concurrent.TimeUnit.SECONDS);
                stringRedisTemplate.opsForValue().set(monthKey, toCent(monthAmount).toString(),
                        secondsUntilMonthExpire(today, zoneId), java.util.concurrent.TimeUnit.SECONDS);
            }
            log.info("reconcileCollectionAmountUsage done, paymentCount={}", paymentIds.size());
        } catch (Exception e) {
            log.warn("reconcileCollectionAmountUsage failed, message={}", e.getMessage());
        }
    }

    private void loadCollectionAmountSumsFromRedis(
            List<Long> paymentIds,
            Map<Long, BigDecimal> dayMap,
            Map<Long, BigDecimal> monthMap,
            long dayStartTime,
            long nextDayStartTime,
            long monthStartTime,
            long nextMonthStartTime,
            ZoneId zoneId,
            LocalDate today) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return;
        }
        long stepStart = System.currentTimeMillis();
        List<Long> missing = new ArrayList<>();
        for (Long paymentId : paymentIds) {
            if (paymentId == null) {
                continue;
            }
            String dayVal = stringRedisTemplate.opsForValue().get(buildCollectionDayKey(paymentId, today));
            String monthVal = stringRedisTemplate.opsForValue().get(buildCollectionMonthKey(paymentId, today));
            if (StringUtils.hasText(dayVal) && StringUtils.hasText(monthVal)) {
                dayMap.put(paymentId, fromCent(dayVal));
                monthMap.put(paymentId, fromCent(monthVal));
            } else {
                log.info("loadCollectionAmountSumsFromRedis cache miss, paymentId={}, hasDay={}, hasMonth={}",
                        paymentId, StringUtils.hasText(dayVal), StringUtils.hasText(monthVal));
                missing.add(paymentId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        List<PaymentAmountAggDto> aggList;
        try {
            stepStart = System.currentTimeMillis();
            aggList = collectionOrderMapper.sumCollectionAmountByPaymentIds(
                    missing, monthStartTime, nextMonthStartTime, dayStartTime, nextDayStartTime);
        } catch (Exception e) {
            log.error("collectionOrderMapper sumCollectionAmountByPaymentIds failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        stepStart = System.currentTimeMillis();
        Map<Long, PaymentAmountAggDto> aggMap = new HashMap<>();
        for (PaymentAmountAggDto dto : CommonUtil.safeList(aggList)) {
            if (dto != null && dto.getPaymentId() != null) {
                aggMap.put(dto.getPaymentId(), dto);
            }
        }
        stepStart = System.currentTimeMillis();
        for (Long paymentId : missing) {
            PaymentAmountAggDto dto = aggMap.get(paymentId);
            BigDecimal dayAmount = dto == null ? BigDecimal.ZERO : CalcUtil.defaultBigDecimal(dto.getDayAmount());
            BigDecimal monthAmount = dto == null ? BigDecimal.ZERO : CalcUtil.defaultBigDecimal(dto.getMonthAmount());
            dayMap.put(paymentId, dayAmount);
            monthMap.put(paymentId, monthAmount);
            String dayKey = buildCollectionDayKey(paymentId, today);
            String monthKey = buildCollectionMonthKey(paymentId, today);
            stringRedisTemplate.opsForValue().set(dayKey, toCent(dayAmount).toString(),
                    secondsUntilDayExpire(today, zoneId), java.util.concurrent.TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(monthKey, toCent(monthAmount).toString(),
                    secondsUntilMonthExpire(today, zoneId), java.util.concurrent.TimeUnit.SECONDS);
            stringRedisTemplate.opsForSet().add(COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY, String.valueOf(paymentId));
            stringRedisTemplate.expire(COLLECTION_LIMIT_ACTIVE_PAYMENT_SET_KEY, COLLECTION_LIMIT_TX_GUARD_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private String buildCollectionDayKey(Long paymentId, LocalDate date) {
        return COLLECTION_LIMIT_DAY_KEY_PREFIX + paymentId + ":" + date;
    }

    private String buildCollectionMonthKey(Long paymentId, LocalDate date) {
        return COLLECTION_LIMIT_MONTH_KEY_PREFIX + paymentId + ":" + date.getYear() + String.format("%02d", date.getMonthValue());
    }

    private BigInteger toCent(BigDecimal amount) {
        return amount.multiply(AMOUNT_SCALE).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
    }

    private BigDecimal fromCent(String centValue) {
        try {
            return new BigDecimal(new BigInteger(centValue)).divide(AMOUNT_SCALE, 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean executeCollectionLimitIncrementLua(
            String guardKey,
            String dayKey,
            String monthKey,
            int txTtlSeconds,
            String deltaCent,
            int dayTtlSeconds,
            int monthTtlSeconds) {
        long stepStart = System.currentTimeMillis();
        List<String> keys = Arrays.asList(guardKey, dayKey, monthKey);
        stepStart = System.currentTimeMillis();
        List result = stringRedisTemplate.execute(
                COLLECTION_LIMIT_INCREMENT_SCRIPT,
                keys,
                String.valueOf(txTtlSeconds),
                deltaCent,
                String.valueOf(dayTtlSeconds),
                String.valueOf(monthTtlSeconds));
        if (result == null || result.isEmpty()) {
            throw new PakGoPayException(ResultCode.FAIL, "collection limit lua returned empty result");
        }
        stepStart = System.currentTimeMillis();
        Object first = result.getFirst();
        long code = first instanceof Number ? ((Number) first).longValue() : Long.parseLong(String.valueOf(first));
        if (code == 0L) {
            return false;
        }
        return true;
    }

    private static RedisScript<List> buildCollectionLimitIncrementScript() {
        String script = """
                local function normalize_num(s)
                    if s == false or s == nil then
                        return "0"
                    end
                    s = tostring(s)
                    s = string.gsub(s, '"', '')
                    s = string.gsub(s, "^%s+", "")
                    s = string.gsub(s, "%s+$", "")
                    if s == "" then
                        return "0"
                    end
                    return s
                end
                                
                local function add_str_int(a, b)
                    local i, j = #a, #b
                    local carry = 0
                    local out = {}
                    while i > 0 or j > 0 or carry > 0 do
                        local x = 0
                        if i > 0 then
                            x = string.byte(a, i) - 48
                            i = i - 1
                        end
                        local y = 0
                        if j > 0 then
                            y = string.byte(b, j) - 48
                            j = j - 1
                        end
                        local s = x + y + carry
                        out[#out + 1] = string.char((s % 10) + 48)
                        carry = math.floor(s / 10)
                    end
                    local r = string.reverse(table.concat(out))
                    r = string.gsub(r, "^0+", "")
                    if r == "" then
                        r = "0"
                    end
                    return r
                end
                                
                if redis.call('SETNX', KEYS[1], '1') == 0 then
                    return {0, "DUPLICATE"}
                end
                                
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
                                
                local delta = normalize_num(ARGV[2])
                local day = normalize_num(redis.call('GET', KEYS[2]))
                local month = normalize_num(redis.call('GET', KEYS[3]))
                                
                day = add_str_int(day, delta)
                month = add_str_int(month, delta)
                                
                redis.call('SET', KEYS[2], day, 'EX', tonumber(ARGV[3]))
                redis.call('SET', KEYS[3], month, 'EX', tonumber(ARGV[4]))
                                
                return {1, day, month}
                """;
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(List.class);
        return redisScript;
    }

    private int secondsUntilDayExpire(LocalDate date, ZoneId zoneId) {
        long now = ZonedDateTime.now(zoneId).toEpochSecond();
        long expiry = date.plusDays(1).atStartOfDay(zoneId).toEpochSecond() + COLLECTION_LIMIT_DAY_KEY_GRACE_SECONDS;
        return (int) Math.max(60, expiry - now);
    }

    private int secondsUntilMonthExpire(LocalDate date, ZoneId zoneId) {
        long now = ZonedDateTime.now(zoneId).toEpochSecond();
        long expiry = date.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toEpochSecond()
                + COLLECTION_LIMIT_MONTH_KEY_GRACE_SECONDS;
        return (int) Math.max(60, expiry - now);
    }

    private Long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * get merchant's payment ids by channel isd
     *
     * @param channelIdList channel ids
     * @param paymentMap    payment map channel (key: payment id value: channel info)
     * @return payment ids
     * @throws PakGoPayException business Exception
     */
    private Set<Long> collectPaymentIdsByChannelIds(List<Long> channelIdList, Map<Long, ChannelDto> paymentMap) throws PakGoPayException {
        List<ChannelDto> channelInfos = loadChannelInfosByIdsWithCache(channelIdList, CommonConstant.ENABLE_STATUS_ENABLE);
        if (channelInfos.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }

        Set<Long> paymentIdList = new HashSet<>();
        channelInfos.forEach(dto -> {
            String ids = dto.getPaymentIds();
            if (!StringUtils.hasText(ids)) {
                return;
            }
            List<Long> tempSet = CommonUtil.parseIds(ids);
            tempSet.forEach(id -> paymentMap.put(id, dto));
            paymentIdList.addAll(tempSet);
        });

        if (paymentIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }
        return paymentIdList;
    }

    private List<ChannelDto> loadChannelInfosByIdsWithCache(List<Long> channelIdList, Integer status) {
        String cacheKey = buildChannelPaymentIdsCacheKey(channelIdList, status);
        try {
            String cached = redisUtil.getValue(cacheKey);
            if (StringUtils.hasText(cached)) {
                return OBJECT_MAPPER.readValue(cached, CHANNEL_LIST_TYPE);
            }
            log.info("loadChannelInfosByIdsWithCache cache miss, key={}, channelIds={}, status={}",
                    cacheKey, channelIdList, status);
        } catch (Exception e) {
            log.warn("loadChannelInfosByIdsWithCache read failed, key={}, message={}", cacheKey, e.getMessage());
        }

        List<ChannelDto> channelInfos = channelMapper.getPaymentIdsByChannelIds(channelIdList, status);
        if (channelInfos == null || channelInfos.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            int ttl = CHANNEL_PAYMENT_IDS_CACHE_TTL_SECONDS + new Random().nextInt(CHANNEL_PAYMENT_IDS_CACHE_JITTER_SECONDS + 1);
            redisUtil.setWithSecondExpire(cacheKey, OBJECT_MAPPER.writeValueAsString(channelInfos), ttl);
        } catch (Exception e) {
            log.warn("loadChannelInfosByIdsWithCache write failed, key={}, message={}", cacheKey, e.getMessage());
        }
        return channelInfos;
    }

    private String buildChannelPaymentIdsCacheKey(List<Long> channelIdList, Integer status) {
        List<Long> normalized = CommonUtil.safeList(channelIdList).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String statusPart = status == null ? "null" : String.valueOf(status);
        String version = resolveChannelPaymentIdsCacheVersion();
        return CHANNEL_PAYMENT_IDS_CACHE_PREFIX + version + ":" + statusPart + ":" + normalized;
    }

    private String resolveChannelPaymentIdsCacheVersion() {
        try {
            String version = redisUtil.getValue(CHANNEL_PAYMENT_IDS_CACHE_VERSION_KEY);
            return StringUtils.hasText(version) ? version : "1";
        } catch (Exception e) {
            log.warn("resolveChannelPaymentIdsCacheVersion failed, message={}", e.getMessage());
            return "1";
        }
    }

    private void bumpChannelPaymentIdsCacheVersion() {
        try {
            redisUtil.increment(CHANNEL_PAYMENT_IDS_CACHE_VERSION_KEY);
        } catch (Exception e) {
            log.warn("bumpChannelPaymentIdsCacheVersion failed, message={}", e.getMessage());
        }
    }

    private List<PaymentDto> loadEnablePaymentsByNoWithCache(Integer supportType, String paymentNo, Set<Long> paymentIdList) {
        String cacheKey = buildEnablePaymentInfoCacheKey(supportType, paymentNo, paymentIdList);
        try {
            String cached = redisUtil.getValue(cacheKey);
            if (StringUtils.hasText(cached)) {
                return OBJECT_MAPPER.readValue(cached, new TypeReference<List<PaymentDto>>() {});
            }
            log.info("loadEnablePaymentsByNoWithCache cache miss, key={}, supportType={}, paymentNo={}, paymentIds={}",
                    cacheKey, supportType, paymentNo, paymentIdList);
        } catch (Exception e) {
            log.warn("loadEnablePaymentsByNoWithCache read failed, key={}, message={}", cacheKey, e.getMessage());
        }

        List<PaymentDto> paymentDtoList = paymentMapper.findEnableInfoByPaymentNos(supportType, paymentNo, paymentIdList);
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            int ttl = ENABLE_PAYMENT_INFO_CACHE_TTL_SECONDS + new Random().nextInt(ENABLE_PAYMENT_INFO_CACHE_JITTER_SECONDS + 1);
            redisUtil.setWithSecondExpire(cacheKey, OBJECT_MAPPER.writeValueAsString(paymentDtoList), ttl);
        } catch (Exception e) {
            log.warn("loadEnablePaymentsByNoWithCache write failed, key={}, message={}", cacheKey, e.getMessage());
        }
        return paymentDtoList;
    }

    private String buildEnablePaymentInfoCacheKey(Integer supportType, String paymentNo, Set<Long> paymentIdList) {
        List<Long> normalized = CommonUtil.safeList(paymentIdList == null ? null : new ArrayList<>(paymentIdList)).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String version = resolveEnablePaymentInfoCacheVersion();
        return ENABLE_PAYMENT_INFO_CACHE_PREFIX + version + ":" + supportType + ":" + paymentNo + ":" + normalized;
    }

    private String resolveEnablePaymentInfoCacheVersion() {
        try {
            String version = redisUtil.getValue(ENABLE_PAYMENT_INFO_CACHE_VERSION_KEY);
            return StringUtils.hasText(version) ? version : "1";
        } catch (Exception e) {
            log.warn("resolveEnablePaymentInfoCacheVersion failed, message={}", e.getMessage());
            return "1";
        }
    }

    private void bumpEnablePaymentInfoCacheVersion() {
        try {
            redisUtil.increment(ENABLE_PAYMENT_INFO_CACHE_VERSION_KEY);
        } catch (Exception e) {
            log.warn("bumpEnablePaymentInfoCacheVersion failed, message={}", e.getMessage());
        }
    }

    // =====================
    // Channel/payment management
    // =====================
    @Override
    public CommonResponse queryChannels(ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        ChannelResponse response = fetchChannelPage(channelQueryRequest);
        return CommonResponse.success(response);
    }

    private ChannelResponse fetchChannelPage(ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        ChannelEntity entity = new ChannelEntity();
        entity.setChannelId(channelQueryRequest.getChannelId());
        entity.setPaymentId(channelQueryRequest.getPaymentId());
        entity.setChannelName(channelQueryRequest.getChannelName());
        entity.setStatus(channelQueryRequest.getStatus());
        entity.setPageNo(channelQueryRequest.getPageNo());
        entity.setPageSize(channelQueryRequest.getPageSize());

        ChannelResponse response = new ChannelResponse();
        try {
            Integer totalNumber = channelMapper.countByQuery(entity);
            List<ChannelDto> channelDtoList = channelMapper.pageByQuery(entity);
            attachPaymentsToChannels(channelDtoList);

            response.setChannelDtoList(channelDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("channelsMapper channelsMapperData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    private void attachPaymentsToChannels(List<ChannelDto> channelDtoList) {
        Map<ChannelDto, List<Long>> channelPaymentIdsMap = new HashMap<>();
        Set<Long> allPaymentIds = new HashSet<>();

        for (ChannelDto channel : channelDtoList) {
            List<Long> ids = CommonUtil.parseIds(channel.getPaymentIds());
            channelPaymentIdsMap.put(channel, ids);
            allPaymentIds.addAll(ids);
        }

        Map<Long, PaymentDto> paymentMap = allPaymentIds.isEmpty()
                ? Collections.emptyMap()
                : paymentMapper.findByPaymentIds(new ArrayList<>(allPaymentIds)).stream()
                .filter(p -> p != null && p.getPaymentId() != null)
                .collect(Collectors.toMap(PaymentDto::getPaymentId, v -> v, (a, b) -> a));
        for (ChannelDto channel : channelDtoList) {
            List<Long> ids = channelPaymentIdsMap.getOrDefault(channel, Collections.emptyList());

            List<PaymentDto> list = channel.getPaymentDtoList();
            if (list == null) {
                list = new ArrayList<>();
                channel.setPaymentDtoList(list);
            }

            for (Long pid : ids) {
                PaymentDto p = paymentMap.get(pid);
                if (p != null) {
                    list.add(p);
                }
            }
        }
    }

    @Override
    public CommonResponse queryPayments(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
        PaymentResponse response = fetchPaymentPage(paymentQueryRequest);
        return CommonResponse.success(response);
    }

    @Override
    /**
     * Query enabled payments available to merchant by merchant channel/agent channel.
     */
    public CommonResponse queryMerchantAvailableChannels(String merchantId) throws PakGoPayException {
        log.info("queryMerchantAvailableChannels start, merchantId={}", merchantId);
        MerchantInfoDto merchantInfo = merchantInfoMapper.findByUserId(merchantId);
        if (merchantInfo == null) {
            log.warn("queryMerchantAvailableChannels merchant not found, merchantId={}", merchantId);
            throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST, "merchant is not exists");
        }

        String channelIds = merchantInfo.getChannelIds();
        log.info("queryMerchantAvailableChannels merchant channelIds, merchantId={}, channelIds={}",
                merchantId, channelIds);
        if (!StringUtils.hasText(channelIds) && StringUtils.hasText(merchantInfo.getParentId())) {
            AgentInfoDto parentAgent = agentInfoMapper.findByUserId(merchantInfo.getParentId());
            if (parentAgent != null) {
                channelIds = parentAgent.getChannelIds();
                log.info("queryMerchantAvailableChannels fallback to parent agent channels, merchantId={}, parentId={}, channelIds={}",
                        merchantId, merchantInfo.getParentId(), channelIds);
            }
        }
        if (!StringUtils.hasText(channelIds)) {
            log.warn("queryMerchantAvailableChannels no available channels, merchantId={}", merchantId);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has no available channel");
        }

        List<Long> channelIdList = CommonUtil.parseIds(channelIds);
        if (channelIdList == null || channelIdList.isEmpty()) {
            log.warn("queryMerchantAvailableChannels parsed channelIds empty, merchantId={}, rawChannelIds={}",
                    merchantId, channelIds);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has no available channel");
        }
        log.info("queryMerchantAvailableChannels parsed channelIds, merchantId={}, channelCount={}",
                merchantId, channelIdList.size());

        Map<Long, ChannelDto> paymentChannelMap = new HashMap<>();
        Set<Long> paymentIds = collectPaymentIdsByChannelIds(channelIdList, paymentChannelMap);
        log.info("queryMerchantAvailableChannels paymentIds resolved, merchantId={}, paymentCount={}",
                merchantId, paymentIds.size());
        List<PaymentDto> paymentDtoList = paymentMapper.findEnabledByPaymentIds(new ArrayList<>(paymentIds));
        paymentDtoList = filterPaymentsByEnableTime(paymentDtoList);
        maskPaymentInterfaceParams(paymentDtoList);
        log.info("queryMerchantAvailableChannels enabled payments filtered, merchantId={}, availablePaymentCount={}",
                merchantId, paymentDtoList.size());

        return CommonResponse.success(paymentDtoList);
    }

    private PaymentResponse fetchPaymentPage(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentName(paymentQueryRequest.getPaymentName());
        entity.setSupportType(paymentQueryRequest.getSupportType());
        entity.setPaymentType(paymentQueryRequest.getPaymentType());
        entity.setCurrency(paymentQueryRequest.getCurrency());
        entity.setStatus(paymentQueryRequest.getStatus());
        entity.setPageNo(paymentQueryRequest.getPageNo());
        entity.setPageSize(paymentQueryRequest.getPageSize());

        PaymentResponse response = new PaymentResponse();
        try {
            Integer totalNumber = paymentMapper.countByQuery(entity);
            List<PaymentDto> paymentDtoList = paymentMapper.pageByQuery(entity);
            maskPaymentInterfaceParams(paymentDtoList);

            response.setPaymentDtoList(paymentDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("paymentMapper paymentMapperData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    private void maskPaymentInterfaceParams(List<PaymentDto> paymentDtoList) {
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            return;
        }
        for (PaymentDto dto : paymentDtoList) {
            if (dto == null) {
                continue;
            }
            dto.setCollectionInterfaceParam(maskInterfaceParamJson(dto.getCollectionInterfaceParam()));
            dto.setPayInterfaceParam(maskInterfaceParamJson(dto.getPayInterfaceParam()));
        }
    }

    private String maskInterfaceParamJson(String interfaceParam) {
        if (interfaceParam == null || interfaceParam.isBlank()) {
            return interfaceParam;
        }
        Object masked = SensitiveDataMaskUtil.sanitizePayload(interfaceParam, INTERFACE_PARAM_MASK_KEYWORDS);
        return masked == null ? null : String.valueOf(masked);
    }

    @Override
    public void exportChannels(ChannelQueryRequest channelQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<ChannelDto> colRes =
                ExportFileUtils.parseColumns(channelQueryRequest, ExportReportDataColumns.CHANNEL_ALLOWED);

        // 2) Init paging params
        channelQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        channelQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                channelQueryRequest,
                (req) -> fetchChannelPage(req).getChannelDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);
    }

    @Override
    public void exportPayments(PaymentQueryRequest paymentQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<PaymentDto> colRes =
                ExportFileUtils.parseColumns(paymentQueryRequest, ExportReportDataColumns.PAYMENT_ALLOWED);

        // 2) Init paging params
        paymentQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        paymentQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                paymentQueryRequest,
                (req) -> fetchPaymentPage(req).getPaymentDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.PAYMENT_EXPORT_FILE_NAME);
    }

    @Override
    public CommonResponse updateChannel(ChannelEditRequest channelEditRequest) throws PakGoPayException {
        ChannelDto channelDto = buildChannelUpdateDto(channelEditRequest);
        try {
            int ret = channelMapper.updateByChannelId(channelDto);
            log.info("updateChannel updateByChannelId done, channelId={}, ret={}", channelEditRequest.getChannelId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "channel not found or no rows updated");
            }
            bumpChannelPaymentIdsCacheVersion();
        } catch (Exception e) {
            log.error("updateChannel updateByChannelId failed, channelId={}", channelEditRequest.getChannelId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto buildChannelUpdateDto(ChannelEditRequest channelEditRequest) throws PakGoPayException {
        ChannelDto dto = new ChannelDto();
        dto.setChannelId(PatchBuilderUtil.parseRequiredLong(channelEditRequest.getChannelId(), "channelId"));
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(channelEditRequest).to(dto)
                .str(channelEditRequest::getChannelName, dto::setChannelName)
                .str(channelEditRequest::getUserName, dto::setUpdateBy)
                .obj(channelEditRequest::getStatus, dto::setStatus)
                .ids(channelEditRequest::getPaymentIds, dto::setPaymentIds)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse updatePayment(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
        PaymentDto paymentDto = buildPaymentUpdateDto(paymentEditRequest);

        try {
            int ret = paymentMapper.updateByPaymentId(paymentDto);
            log.info("updatePayment updateByPaymentId done, paymentId={}, ret={}", paymentEditRequest.getPaymentId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "payment not found or no rows updated");
            }
            bumpEnablePaymentInfoCacheVersion();
        } catch (Exception e) {
            log.error("updatePayment updateByChannelId failed, channelId={}", paymentEditRequest.getPaymentId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto buildPaymentUpdateDto(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
        PaymentDto dto = new PaymentDto();
        Long paymentId = PatchBuilderUtil.parseRequiredLong(paymentEditRequest.getPaymentId(), "paymentId");
        dto.setPaymentId(paymentId);
        dto.setUpdateTime(System.currentTimeMillis() / 1000);
        PaymentDto current = paymentMapper.findByPaymentId(paymentId);
        if (current == null) {
            throw new PakGoPayException(ResultCode.FAIL, "payment not found");
        }
        paymentEditRequest.setCollectionInterfaceParam(mergeMaskedInterfaceParams(
                current.getCollectionInterfaceParam(),
                paymentEditRequest.getCollectionInterfaceParam(),
                "collectionInterfaceParam"));
        paymentEditRequest.setPayInterfaceParam(mergeMaskedInterfaceParams(
                current.getPayInterfaceParam(),
                paymentEditRequest.getPayInterfaceParam(),
                "payInterfaceParam"));

        return PatchBuilderUtil.from(paymentEditRequest).to(dto)
                .str(paymentEditRequest::getPaymentNo, dto::setPaymentNo)
                .str(paymentEditRequest::getPaymentName, dto::setPaymentName)
                .str(paymentEditRequest::getUserName, dto::setUpdateBy)
                .obj(paymentEditRequest::getStatus, dto::setStatus)
                .obj(paymentEditRequest::getSupportType, dto::setSupportType)
                .str(paymentEditRequest::getPaymentType, dto::setPaymentType)
                .str(paymentEditRequest::getBankName, dto::setBankName)
                .str(paymentEditRequest::getBankAccount, dto::setBankAccount)
                .str(paymentEditRequest::getBankUserName, dto::setBankUserName)
                .str(paymentEditRequest::getEnableTimePeriod, dto::setEnableTimePeriod)
                .str(paymentEditRequest::getPayInterfaceParam, dto::setPayInterfaceParam)
                .str(paymentEditRequest::getCollectionInterfaceParam, dto::setCollectionInterfaceParam)
                .obj(paymentEditRequest::getIsCheckoutCounter, dto::setIsCheckoutCounter)
                .obj(paymentEditRequest::getCollectionDailyLimit, dto::setCollectionDailyLimit)
                .obj(paymentEditRequest::getPayDailyLimit, dto::setPayDailyLimit)
                .obj(paymentEditRequest::getCollectionMonthlyLimit, dto::setCollectionMonthlyLimit)
                .obj(paymentEditRequest::getPayMonthlyLimit, dto::setPayMonthlyLimit)
                .str(paymentEditRequest::getPaymentRequestPayUrl, dto::setPaymentRequestPayUrl)
                .str(paymentEditRequest::getPaymentRequestCollectionUrl, dto::setPaymentRequestCollectionUrl)
                .str(paymentEditRequest::getPaymentPayRate, dto::setPaymentPayRate)
                .str(paymentEditRequest::getPaymentCollectionRate, dto::setPaymentCollectionRate)
                .str(paymentEditRequest::getPaymentCheckPayUrl, dto::setPaymentCheckPayUrl)
                .str(paymentEditRequest::getPaymentCheckCollectionUrl, dto::setPaymentCheckCollectionUrl)
                .str(paymentEditRequest::getBalanceQueryUrl, dto::setBalanceQueryUrl)
                .obj(paymentEditRequest::getPaymentMaxAmount, dto::setPaymentMaxAmount)
                .obj(paymentEditRequest::getPaymentMinAmount, dto::setPaymentMinAmount)
                .str(paymentEditRequest::getIsThird, dto::setIsThird)
                .str(paymentEditRequest::getCollectionCallbackAddr, dto::setCollectionCallbackAddr)
                .str(paymentEditRequest::getPayCallbackAddr, dto::setPayCallbackAddr)
                .str(paymentEditRequest::getCheckoutCounterUrl, dto::setCheckoutCounterUrl)
                .str(paymentEditRequest::getCurrency, dto::setCurrency)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    private String mergeMaskedInterfaceParams(String dbJson, String requestJson, String fieldName)
            throws PakGoPayException {
        if (requestJson == null || requestJson.isBlank()) {
            return requestJson;
        }
        Map<String, Object> requestMap = parseInterfaceParamJson(requestJson, fieldName + " request");
        Map<String, Object> dbMap = parseInterfaceParamJsonSafe(dbJson, fieldName + " db");
        Map<String, Object> mergedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : requestMap.entrySet()) {
            String key = entry.getKey();
            Object requestValue = entry.getValue();
            if (isMaskedValue(requestValue) && dbMap.containsKey(key)) {
                mergedMap.put(key, dbMap.get(key));
            } else {
                mergedMap.put(key, requestValue);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(mergedMap);
        } catch (Exception e) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS,
                    fieldName + " serialize failed: " + e.getMessage());
        }
    }

    private Map<String, Object> parseInterfaceParamJson(String json, String scene) throws PakGoPayException {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, scene + " parse failed: " + e.getMessage());
        }
    }

    private Map<String, Object> parseInterfaceParamJsonSafe(String json, String scene) throws PakGoPayException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return parseInterfaceParamJson(json, scene);
    }

    private boolean isMaskedValue(Object value) {
        return value instanceof String && ((String) value).contains("*");
    }

    @Override
    public CommonResponse createChannel(ChannelAddRequest channelAddRequest) throws PakGoPayException {
        ChannelDto channelDto = buildChannelCreateDto(channelAddRequest);
        try {
            int ret = channelMapper.insert(channelDto);
            log.info("createChannel insert done, ret={}", ret);
            bumpChannelPaymentIdsCacheVersion();
        } catch (Exception e) {
            log.error("createChannel insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto buildChannelCreateDto(ChannelAddRequest channelAddRequest) {
        ChannelDto dto = new ChannelDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<ChannelAddRequest, ChannelDto> builder = PatchBuilderUtil.from(channelAddRequest).to(dto)
                .str(channelAddRequest::getChannelName, dto::setChannelName)
                .obj(channelAddRequest::getStatus, dto::setStatus)
                .ids(channelAddRequest::getPaymentIds, dto::setPaymentIds)

                // 10) Meta Info
                .obj(channelAddRequest::getRemark, dto::setRemark)
                .obj(() -> now, dto::setCreateTime)
                .obj(() -> now, dto::setUpdateTime)
                .str(channelAddRequest::getUserName, dto::setCreateBy)
                .str(channelAddRequest::getUserName, dto::setUpdateBy);

        return builder.build();
    }

    @Override
    public CommonResponse createPayment(PaymentAddRequest paymentAddRequest) throws PakGoPayException {
        PaymentDto paymentDto = buildPaymentCreateDto(paymentAddRequest);
        try {
            int ret = paymentMapper.insert(paymentDto);
            log.info("createPayment insert done, ret={}", ret);
            bumpEnablePaymentInfoCacheVersion();
        } catch (Exception e) {
            log.error("createPayment insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto buildPaymentCreateDto(
            PaymentAddRequest paymentAddRequest) throws PakGoPayException {
        PaymentDto dto = new PaymentDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder = PatchBuilderUtil.from(paymentAddRequest).to(dto)

                // 1) Basic Info
                .str(paymentAddRequest::getPaymentNo, dto::setPaymentNo)
                .str(paymentAddRequest::getPaymentName, dto::setPaymentName)
                .str(paymentAddRequest::getCurrency, dto::setCurrency)
                .str(paymentAddRequest::getPaymentType, dto::setPaymentType)
                .str(paymentAddRequest::getIsThird, dto::setIsThird)

                // 2) Status & Capability
                .obj(paymentAddRequest::getStatus, dto::setStatus)
                .obj(paymentAddRequest::getSupportType, dto::setSupportType)
                .obj(paymentAddRequest::getIsCheckoutCounter, dto::setIsCheckoutCounter)
                .str(paymentAddRequest::getEnableTimePeriod, dto::setEnableTimePeriod)
                .str(paymentAddRequest::getBalanceQueryUrl, dto::setBalanceQueryUrl)

                // 3) Amount Limits (optional)
                .obj(paymentAddRequest::getPaymentMaxAmount, dto::setPaymentMaxAmount)
                .obj(paymentAddRequest::getPaymentMinAmount, dto::setPaymentMinAmount)

                // 10) Meta Info
                .obj(paymentAddRequest::getRemark, dto::setRemark)
                .obj(() -> now, dto::setCreateTime)
                .obj(() -> now, dto::setUpdateTime)
                .str(paymentAddRequest::getUserName, dto::setCreateBy)
                .str(paymentAddRequest::getUserName, dto::setUpdateBy);

        dto.setOrderQuantity(0L);
        dto.setSuccessQuantity(0L);

        // supportType routing
        Integer supportType = paymentAddRequest.getSupportType();
        if (supportType == 0 || supportType == 2) {
            applyCollectionRequiredFields(builder, paymentAddRequest);
        }
        if (supportType == 1 || supportType == 2) {
            applyPayRequiredFields(builder, paymentAddRequest);
        }

        // Checkout counter requirement
        builder.ifTrue(Integer.valueOf(1).equals(paymentAddRequest.getIsCheckoutCounter()))
                .reqStr("checkoutCounterUrl", paymentAddRequest::getCheckoutCounterUrl, dto::setCheckoutCounterUrl);

        // Bank info requirement (no lambda, no swallowing)
        builder.ifTrue(CommonConstant.PAYMENT_TYPE_BANK.equals(paymentAddRequest.getPaymentType()))
                .reqStr("bankName", paymentAddRequest::getBankName, dto::setBankName)
                .reqStr("bankAccount", paymentAddRequest::getBankAccount, dto::setBankAccount)
                .reqStr("bankUserName", paymentAddRequest::getBankUserName, dto::setBankUserName)
                .endSkip();

        return builder.build();
    }

    private void applyPayRequiredFields(
            PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder, PaymentAddRequest req) throws PakGoPayException {
        PaymentDto dto = builder.dto();

        builder.reqObj("payDailyLimit", req::getPayDailyLimit, dto::setPayDailyLimit)
                .reqObj("payMonthlyLimit", req::getPayMonthlyLimit, dto::setPayMonthlyLimit)
                .reqStr("paymentPayRate", req::getPaymentPayRate, dto::setPaymentPayRate)
                .reqStr("paymentRequestPayUrl", req::getPaymentRequestPayUrl, dto::setPaymentRequestPayUrl)
                .reqStr("paymentCheckPayUrl", req::getPaymentCheckPayUrl, dto::setPaymentCheckPayUrl)
                .reqStr("payInterfaceParam", req::getPayInterfaceParam, dto::setPayInterfaceParam)
                .reqStr("payCallbackAddr", req::getPayCallbackAddr, dto::setPayCallbackAddr);
    }

    private void applyCollectionRequiredFields(
            PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder, PaymentAddRequest req) throws PakGoPayException {
        PaymentDto dto = builder.dto();

        builder.reqObj("collectionDailyLimit", req::getCollectionDailyLimit, dto::setCollectionDailyLimit)
                .reqObj("collectionMonthlyLimit", req::getCollectionMonthlyLimit, dto::setCollectionMonthlyLimit)
                .reqStr("paymentCollectionRate", req::getPaymentCollectionRate, dto::setPaymentCollectionRate)
                .reqStr("paymentRequestCollectionUrl", req::getPaymentRequestCollectionUrl, dto::setPaymentRequestCollectionUrl)
                .reqStr("paymentCheckCollectionUrl", req::getPaymentCheckCollectionUrl, dto::setPaymentCheckCollectionUrl)
                .reqStr("collectionInterfaceParam", req::getCollectionInterfaceParam, dto::setCollectionInterfaceParam)
                .reqStr("collectionCallbackAddr", req::getCollectionCallbackAddr, dto::setCollectionCallbackAddr);
    }
}
