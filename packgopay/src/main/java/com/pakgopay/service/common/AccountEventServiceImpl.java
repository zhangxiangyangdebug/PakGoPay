package com.pakgopay.service.common;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.AccountEventStatus;
import com.pakgopay.common.enums.AccountEventType;
import com.pakgopay.data.response.AccountEventDetailResponse;
import com.pakgopay.data.response.AccountEventQueryResponse;
import com.pakgopay.mapper.AccountEventMapper;
import com.pakgopay.mapper.secondary.BalanceChangeLogMapper;
import com.pakgopay.mapper.dto.AccountEventDto;
import com.pakgopay.mapper.dto.AccountEventQueryDto;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.BalanceChangeLogDto;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.BalanceBucketSelectService.BucketSelectAction;
import com.pakgopay.timer.PartitionMaintenanceTimer;
import com.pakgopay.util.CalcUtil;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountEventServiceImpl implements AccountEventService {

    // Batch number format: EVENT_yyyyMMddHHmmss_user_currency_xxxxxxxx
    private static final DateTimeFormatter BATCH_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withLocale(Locale.ROOT)
            .withZone(java.time.ZoneOffset.UTC);
    private static final DateTimeFormatter PARTITION_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final String EVENT_KEY_SEPARATOR = "|";
    private static final int MAX_ERROR_TEXT = 500;
    private static final int CREDIT_GROUP_BUCKET_COUNT = 4;
    private static final int CLAIM_PREVIOUS_MONTHS_ALWAYS = 1;
    private static final int CLAIM_EXTRA_PREVIOUS_MONTH_EVERY = 5;
    private static final int CLAIM_OLDER_MONTH_EVERY = 20;
    private static final int CLAIM_OLDER_MONTH_LOOKBACK = 24;
    private static final int CLAIM_WEIGHT_CURRENT = 60;
    private static final int CLAIM_WEIGHT_PREVIOUS = 25;
    private static final int CLAIM_WEIGHT_PREVIOUS_EXTRA = 10;
    private static final int CLAIM_WEIGHT_OLDER = 5;
    private static final String CONSUME_TICK_KEY = "job:account_event_consume:tick";
    private static final long CONSUME_TICK_CYCLE = 1000L;

    @Autowired
    private AccountEventMapper accountEventMapper;

    @Autowired
    private BalanceChangeLogMapper secondaryBalanceChangeLogMapper;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceBucketSelectService balanceBucketSelectService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Collection success creates:
     * 1) merchant credit event
     * 2) optional agent fee events by level.
     */
    @Override
    public void appendCollectionSuccessEvents(CollectionOrderDto collectionOrderDto, MerchantInfoDto merchantInfoDto) {
        if (collectionOrderDto == null) {
            return;
        }
        Long eventCreatedAt = resolveEventCreatedAt(collectionOrderDto.getCreateTime(), collectionOrderDto.getUpdateTime());
        List<AccountEventDto> eventsToAppend = new ArrayList<>();
        BigDecimal creditAmount = CalcUtil.safeSubtract(
                resolveOrderAmount(collectionOrderDto.getActualAmount(), collectionOrderDto.getAmount()),
                collectionOrderDto.getMerchantFee());
        appendEvent(eventsToAppend,
                AccountEventType.COLLECTION_CREDIT,
                collectionOrderDto.getTransactionNo() + ":MCH",
                collectionOrderDto.getMerchantUserId(),
                collectionOrderDto.getCurrencyType(),
                creditAmount,
                collectionOrderDto.getSuccessCallbackTime() == null
                        ? collectionOrderDto.getUpdateTime()
                        : collectionOrderDto.getSuccessCallbackTime(),
                eventCreatedAt);

        appendAgentCreditEvents(
                eventsToAppend,
                collectionOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                collectionOrderDto.getCurrencyType(),
                collectionOrderDto.getAgent1Fee(),
                collectionOrderDto.getAgent2Fee(),
                collectionOrderDto.getAgent3Fee(),
                eventCreatedAt);
        appendEvents(eventsToAppend);
    }

    /**
     * Payout success creates:
     * 1) merchant payout confirm event (frozen -> deducted)
     * 2) optional agent fee events by level.
     */
    @Override
    public void appendPayoutSuccessEvents(PayOrderDto payOrderDto, MerchantInfoDto merchantInfoDto, BigDecimal frozenAmount) {
        if (payOrderDto == null) {
            return;
        }
        Long eventCreatedAt = resolveEventCreatedAt(payOrderDto.getCreateTime(), payOrderDto.getUpdateTime());
        List<AccountEventDto> eventsToAppend = new ArrayList<>();
        appendEvent(eventsToAppend,
                AccountEventType.PAYOUT_CONFIRM,
                payOrderDto.getTransactionNo() + ":MCH_CONFIRM",
                payOrderDto.getMerchantUserId(),
                payOrderDto.getCurrencyType(),
                frozenAmount,
                payOrderDto.getSuccessCallbackTime() == null
                        ? payOrderDto.getUpdateTime()
                        : payOrderDto.getSuccessCallbackTime(),
                eventCreatedAt);

        appendAgentCreditEvents(
                eventsToAppend,
                payOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                payOrderDto.getCurrencyType(),
                payOrderDto.getAgent1Fee(),
                payOrderDto.getAgent2Fee(),
                payOrderDto.getAgent3Fee(),
                eventCreatedAt);
        appendEvents(eventsToAppend);
    }

    /**
     * Payout failed creates merchant release event (frozen -> available rollback).
     */
    @Override
    public void appendPayoutFailedEvents(PayOrderDto payOrderDto, BigDecimal frozenAmount) {
        if (payOrderDto == null) {
            return;
        }
        Long eventCreatedAt = resolveEventCreatedAt(payOrderDto.getCreateTime(), payOrderDto.getUpdateTime());
        List<AccountEventDto> eventsToAppend = new ArrayList<>();
        appendEvent(eventsToAppend,
                AccountEventType.PAYOUT_RELEASE,
                payOrderDto.getTransactionNo() + ":MCH_RELEASE",
                payOrderDto.getMerchantUserId(),
                payOrderDto.getCurrencyType(),
                frozenAmount,
                payOrderDto.getUpdateTime(),
                eventCreatedAt);
        appendEvents(eventsToAppend);
    }

    /**
     * Scheduler entry:
     * claim pending/failed events, process in grouped batches.
     */
    @Override
    public int consumePendingEvents(int limitSize, int maxRounds) {
        int totalConsumed = 0;
        int rounds = Math.max(maxRounds, 1);
        int claimLimit = Math.max(limitSize, 1);
        long tick = nextConsumeTick();
        for (int i = 0; i < rounds; i++) {
            long now = System.currentTimeMillis() / 1000;
            List<AccountEventDto> claimed = claimPendingEventsByPartitions(claimLimit, now, tick);
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            totalConsumed += claimed.size();
            processClaimedEventBatch(claimed, now);
        }
        return totalConsumed;
    }

    @Override
    public AccountEventQueryResponse listByTransactionNo(String transactionNo) {
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        if (range == null) {
            throw new IllegalArgumentException("invalid transactionNo");
        }
        List<AccountEventQueryDto> queryDtos = accountEventMapper.listByTransactionNo(transactionNo, range[0], range[1]);
        AccountEventQueryResponse response = new AccountEventQueryResponse();
        response.setTransactionNo(transactionNo);
        response.setAccountEvents(queryDtos.stream().map(this::toAccountEventDetailResponse).collect(Collectors.toList()));
        return response;
    }

    private long nextConsumeTick() {
        try {
            RAtomicLong tickCounter = redissonClient.getAtomicLong(CONSUME_TICK_KEY);
            long rawTick = tickCounter.incrementAndGet();
            return Math.floorMod(rawTick - 1, CONSUME_TICK_CYCLE) + 1;
        } catch (Exception e) {
            log.warn("account event consume tick fallback to current time, message={}", e.getMessage());
            return Math.floorMod(System.currentTimeMillis() / 1000 - 1, CONSUME_TICK_CYCLE) + 1;
        }
    }

    private List<AccountEventDto> claimPendingEventsByPartitions(int claimLimit, long now, long tick) {
        List<AccountEventDto> claimed = new ArrayList<>();
        List<ClaimPartitionPlan> plans = resolveClaimPartitionPlans(now, tick);
        List<String> claimDetails = new ArrayList<>(plans.size());
        int remainingBudget = claimLimit;
        int remainingWeight = plans.stream().mapToInt(plan -> plan.weight).sum();
        for (ClaimPartitionPlan plan : plans) {
            if (remainingBudget <= 0 || remainingWeight <= 0) {
                break;
            }
            int targetLimit = Math.max((int) Math.ceil((double) remainingBudget * plan.weight / remainingWeight), 1);
            try {
                List<AccountEventDto> partitionClaimed = accountEventMapper.claimPendingEventsFromTable(
                        plan.tableName,
                        AccountEventType.allCodes(),
                        targetLimit,
                        now);
                if (partitionClaimed != null && !partitionClaimed.isEmpty()) {
                    claimed.addAll(partitionClaimed);
                    remainingBudget -= partitionClaimed.size();
                    claimDetails.add(plan.tableName + "(weight=" + plan.weight + ",target=" + targetLimit + ",claimed=" + partitionClaimed.size() + ")");
                } else {
                    claimDetails.add(plan.tableName + "(weight=" + plan.weight + ",target=" + targetLimit + ",claimed=0)");
                }
            } catch (Exception e) {
                log.warn("account event claim skipped, tableName={}, message={}", plan.tableName, e.getMessage());
                claimDetails.add(plan.tableName + "(weight=" + plan.weight + ",target=" + targetLimit + ",claimed=skip)");
            }
            remainingWeight -= plan.weight;
        }
        if (!claimDetails.isEmpty()) {
            log.info("account event claim plan, tick={}, claimLimit={}, claimed={}, plans={}",
                    tick, claimLimit, claimed.size(), String.join(", ", claimDetails));
        }
        return claimed;
    }

    private List<ClaimPartitionPlan> resolveClaimPartitionPlans(long now, long tick) {
        Instant current = Instant.ofEpochSecond(now);
        Set<String> existingPartitionTables = loadCachedAccountEventPartitions();
        LinkedHashMap<String, Integer> weightedPlans = new LinkedHashMap<>();
        if (tick % CLAIM_EXTRA_PREVIOUS_MONTH_EVERY == 0) {
            addClaimPartitionPlanIfExists(
                    weightedPlans,
                    existingPartitionTables,
                    "account_event_" + PARTITION_SUFFIX_FORMATTER.format(current.atZone(ZoneOffset.UTC).minusMonths(2)),
                    CLAIM_WEIGHT_PREVIOUS_EXTRA);
        }
        if (tick % CLAIM_OLDER_MONTH_EVERY == 0) {
            long olderCycleIndex = Math.max(tick / CLAIM_OLDER_MONTH_EVERY - 1, 0);
            int olderMonths = 3 + (int) (olderCycleIndex % CLAIM_OLDER_MONTH_LOOKBACK);
            addClaimPartitionPlanIfExists(
                    weightedPlans,
                    existingPartitionTables,
                    "account_event_" + PARTITION_SUFFIX_FORMATTER.format(current.atZone(ZoneOffset.UTC).minusMonths(olderMonths)),
                    CLAIM_WEIGHT_OLDER);
        }
        for (int i = CLAIM_PREVIOUS_MONTHS_ALWAYS; i >= 1; i--) {
            addClaimPartitionPlanIfExists(
                    weightedPlans,
                    existingPartitionTables,
                    "account_event_" + PARTITION_SUFFIX_FORMATTER.format(current.atZone(ZoneOffset.UTC).minusMonths(i)),
                    CLAIM_WEIGHT_PREVIOUS);
        }
        addClaimPartitionPlanIfExists(
                weightedPlans,
                existingPartitionTables,
                "account_event_" + resolvePartitionSuffix(current.getEpochSecond()),
                CLAIM_WEIGHT_CURRENT);

        List<ClaimPartitionPlan> plans = new ArrayList<>(weightedPlans.size());
        for (Map.Entry<String, Integer> entry : weightedPlans.entrySet()) {
            plans.add(new ClaimPartitionPlan(entry.getKey(), entry.getValue()));
        }
        return plans;
    }

    private void addClaimPartitionPlanIfExists(
            Map<String, Integer> weightedPlans,
            Set<String> existingPartitionTables,
            String tableName,
            int weight) {
        if (existingPartitionTables == null || existingPartitionTables.isEmpty() || existingPartitionTables.contains(tableName)) {
            weightedPlans.put(tableName, weight);
        }
    }

    private Set<String> loadCachedAccountEventPartitions() {
        try {
            return redisTemplate.opsForSet().members(PartitionMaintenanceTimer.ACCOUNT_EVENT_PARTITION_CACHE_KEY);
        } catch (Exception e) {
            log.warn("load account event partition cache failed, message={}", e.getMessage());
            return null;
        }
    }

    private AccountEventDetailResponse toAccountEventDetailResponse(AccountEventQueryDto dto) {
        AccountEventDetailResponse response = new AccountEventDetailResponse();
        response.setUserName(dto.getUserName());
        response.setRoleId(dto.getRoleId());
        response.setCurrency(dto.getCurrency());
        response.setAmount(dto.getAmount());
        response.setEventType(dto.getEventType());
        return response;
    }

    /**
     * Process one claimed chunk:
     * group by (eventType,userId,currency), then apply each group in one transaction.
     */
    private void processClaimedEventBatch(List<AccountEventDto> claimed, long now) {
        Map<String, EventGroup> grouped = groupEvents(claimed);
        for (EventGroup group : grouped.values()) {
            processOneEventGroup(group, now);
        }
    }

    /**
     * Execute one aggregate group:
     * - apply balance change
     * - mark events done
     * - best-effort write balance_change_log
     * On failure: mark all events as failed with retry_count+1.
     */
    private void processOneEventGroup(EventGroup group, long now) {
        if (group.ids == null || group.ids.isEmpty()) {
            log.warn("skip empty account event group, eventType={}, userId={}, currency={}, bucketNo={}, createTime={}",
                    group.eventType, group.userId, group.currency, group.bucketNo, group.createTime);
            return;
        }
        String batchNo = buildBatchNo(group.eventType, group.userId, group.currency);
        long partitionStart = resolvePartitionStart(group.createTime);
        long partitionEnd = resolvePartitionEnd(group.createTime);
        try {
            transactionUtil.runInTransaction(() -> {
                applyBalanceChange(group, batchNo);
                accountEventMapper.markDoneByIds(group.ids, batchNo, now, partitionStart, partitionEnd);
            });
            writeBalanceChangeLog(group, batchNo, now);
            log.info("account event batch done, batchNo={}, eventType={}, userId={}, currency={}, bucketNo={}, count={}, amount={}, createTime={}",
                    batchNo, group.eventType, group.userId, group.currency, group.bucketNo, group.ids.size(), group.totalAmount, group.createTime);
        } catch (Exception e) {
            String error = trimError(e == null ? null : e.getMessage());
            accountEventMapper.markFailedByIds(group.ids, error, now, partitionStart, partitionEnd);
            log.warn("account event batch failed, batchNo={}, eventType={}, userId={}, currency={}, bucketNo={}, count={}, createTime={}, error={}",
                    batchNo, group.eventType, group.userId, group.currency, group.bucketNo, group.ids.size(), group.createTime, error);
        }
    }

    /**
     * Map grouped event type to balance operation.
     */
    private void applyBalanceChange(EventGroup group, String batchNo) {
        CommonUtil.withBalanceLogContext("account_event.consume", group.routeKey, () -> {
            if (AccountEventType.COLLECTION_CREDIT.getCode().equals(group.eventType)) {
                balanceService.creditBalanceWithoutSplit(group.userId, group.currency, group.totalAmount, group.bucketNo);
                return;
            }
            if (AccountEventType.AGENT_CREDIT.getCode().equals(group.eventType)) {
                balanceService.creditBalance(group.userId, group.currency, group.totalAmount, group.bucketNo);
                return;
            }
            if (AccountEventType.PAYOUT_CONFIRM.getCode().equals(group.eventType)) {
                balanceService.confirmPayoutBalance(group.userId, group.currency, group.totalAmount, group.bucketNo);
                return;
            }
            if (AccountEventType.PAYOUT_RELEASE.getCode().equals(group.eventType)) {
                balanceService.releaseFrozenBalance(group.userId, group.currency, group.totalAmount, group.bucketNo);
                return;
            }
            throw new IllegalArgumentException("unsupported event type: " + group.eventType);
        });
    }

    /**
     * Write one summarized change record to secondary balance_change_log.
     * This is best-effort and must not break accounting commit result.
     */
    private void writeBalanceChangeLog(EventGroup group, String batchNo, long now) {
        try {
            BalanceChangeLogDto logDto = new BalanceChangeLogDto();
            logDto.setMerchantUserId(group.userId);
            logDto.setCurrency(group.currency);
            logDto.setBizType(group.eventType);
            logDto.setBizNo(batchNo);
            logDto.setChangeAmount(group.totalAmount);
            logDto.setCreatedAt(now);
            secondaryBalanceChangeLogMapper.insertIgnore(logDto);
        } catch (Exception e) {
            log.warn("balance_change_log write failed, batchNo={}, eventType={}, userId={}, message={}",
                    batchNo, group.eventType, group.userId, e.getMessage());
        }
    }

    /**
     * Group claim rows in 2 stages:
     * 1) coarse group by (eventType,userId,currency,month partition)
     * 2) for credit-like events, load 4 lowest buckets once and distribute rows among them
     * 3) for other events, keep original single-bucket behavior
     */
    private Map<String, EventGroup> groupEvents(List<AccountEventDto> events) {
        Map<String, PendingGroup> pendingGroups = new LinkedHashMap<>();
        for (AccountEventDto event : events) {
            if (event == null
                    || event.getId() == null
                    || event.getAmount() == null
                    || event.getEventType() == null
                    || event.getUserId() == null
                    || event.getCurrency() == null
                    || event.getCreatedAt() == null
                    || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String partitionSuffix = resolvePartitionSuffix(event.getCreatedAt());
            String key = buildPendingGroupKey(event.getEventType(), event.getUserId(), event.getCurrency(), partitionSuffix);
            PendingGroup group = pendingGroups.computeIfAbsent(key, k -> new PendingGroup(
                    event.getEventType(),
                    event.getUserId(),
                    event.getCurrency(),
                    event.getCreatedAt()));
            group.events.add(event);
            group.totalAmount = group.totalAmount.add(event.getAmount());
        }

        Map<String, EventGroup> grouped = new LinkedHashMap<>();
        for (PendingGroup pendingGroup : pendingGroups.values()) {
            if (isCreditEvent(pendingGroup.eventType)) {
                distributeCreditEvents(grouped, pendingGroup);
                continue;
            }
            Integer bucketNo = balanceBucketSelectService.selectBucketNo(
                    pendingGroup.userId,
                    pendingGroup.currency,
                    pendingGroup.totalAmount,
                    resolveBucketSelectAction(pendingGroup.eventType));
            String routeKey = buildRouteKey(pendingGroup.eventType, pendingGroup.userId, pendingGroup.currency, bucketNo);
            String key = buildEventGroupKey(
                    pendingGroup.eventType,
                    pendingGroup.userId,
                    pendingGroup.currency,
                    bucketNo,
                    resolvePartitionSuffix(pendingGroup.createTime));
            EventGroup group = grouped.computeIfAbsent(key, k -> new EventGroup(
                    pendingGroup.eventType,
                    pendingGroup.userId,
                    pendingGroup.currency,
                    routeKey,
                    bucketNo,
                    pendingGroup.createTime));
            for (AccountEventDto event : pendingGroup.events) {
                appendEventToGroup(group, event);
            }
        }
        return grouped;
    }

    /**
     * Build optional 3-level agent fee events in deterministic order.
     */
    private void appendAgentCreditEvents(
            List<AccountEventDto> eventsToAppend,
            String transactionNo,
            List<AgentInfoDto> agentInfos,
            String currency,
            BigDecimal agent1Fee,
            BigDecimal agent2Fee,
            BigDecimal agent3Fee,
            Long eventCreatedAt) {
        if (agentInfos == null || agentInfos.isEmpty()) {
            return;
        }
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_FIRST, agent1Fee, currency, eventCreatedAt);
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_SECOND, agent2Fee, currency, eventCreatedAt);
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_THIRD, agent3Fee, currency, eventCreatedAt);
    }

    /**
     * Append one level fee event when fee > 0 and target level agent exists.
     */
    private void appendOneAgentFee(
            List<AccountEventDto> eventsToAppend,
            String transactionNo,
            List<AgentInfoDto> agentInfos,
            Integer targetLevel,
            BigDecimal fee,
            String currency,
            Long eventCreatedAt) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        for (AgentInfoDto agent : agentInfos) {
            if (agent != null && Objects.equals(targetLevel, agent.getLevel())) {
                appendEvent(eventsToAppend,
                        AccountEventType.AGENT_CREDIT,
                        transactionNo + ":AGENT_L" + targetLevel,
                        agent.getUserId(),
                        currency,
                        fee,
                        System.currentTimeMillis() / 1000,
                        eventCreatedAt);
                return;
            }
        }
    }

    /**
     * Event append with data guard + idempotent insert.
     */
    private void appendEvent(
            List<AccountEventDto> eventsToAppend,
            AccountEventType eventType,
            String bizNo,
            String userId,
            String currency,
            BigDecimal amount,
            Long eventTime,
            Long createdAt) {
        if (eventType == null || userId == null || userId.isBlank()
                || currency == null || currency.isBlank()
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long eventCreatedAt = createdAt == null ? now : createdAt;
        AccountEventDto dto = new AccountEventDto();
        dto.setEventType(eventType.getCode());
        dto.setBizNo(bizNo);
        dto.setUserId(userId);
        dto.setCurrency(currency);
        dto.setAmount(amount);
        dto.setEventTime(eventTime == null ? now : eventTime);
        dto.setStatus(AccountEventStatus.PENDING.getCode());
        dto.setRetryCount(0);
        dto.setCreatedAt(eventCreatedAt);
        dto.setUpdatedAt(now);
        eventsToAppend.add(dto);
    }

    /**
     * Batch append prepared events.
     * Duplicate business keys are ignored by mapper-side idempotent SQL.
     */
    private void appendEvents(List<AccountEventDto> eventsToAppend) {
        if (eventsToAppend == null || eventsToAppend.isEmpty()) {
            return;
        }
        String tableName = resolvePartitionTableName(eventsToAppend);
        int inserted = accountEventMapper.insertEventBatch(tableName, eventsToAppend);
        int duplicated = eventsToAppend.size() - inserted;
        if (duplicated > 0) {
            log.info("account event batch append done, inserted={}, duplicated={}", inserted, duplicated);
        }
    }

    private String resolvePartitionTableName(List<AccountEventDto> eventsToAppend) {
        Long createdAt = eventsToAppend.get(0).getCreatedAt();
        if (createdAt == null) {
            throw new IllegalArgumentException("account event createdAt is required");
        }
        String suffix = PARTITION_SUFFIX_FORMATTER.format(Instant.ofEpochSecond(createdAt));
        String tableName = "account_event_" + suffix;
        for (AccountEventDto event : eventsToAppend) {
            if (event == null || event.getCreatedAt() == null) {
                throw new IllegalArgumentException("account event createdAt is required");
            }
            String currentSuffix = PARTITION_SUFFIX_FORMATTER.format(Instant.ofEpochSecond(event.getCreatedAt()));
            if (!suffix.equals(currentSuffix)) {
                throw new IllegalArgumentException("account event batch spans multiple month partitions");
            }
        }
        return tableName;
    }

    /**
     * Human-readable batchNo for observability and reconciliation.
     */
    private String buildBatchNo(String eventType, String userId, String currency) {
        String ts = BATCH_TS_FORMATTER.format(Instant.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return eventType + "_" + ts + "_" + userId + "_" + currency + "_" + random;
    }

    /**
     * Keep failure reason compact to fit DB field and logs.
     */
    private String trimError(String error) {
        if (error == null) {
            return "unknown_error";
        }
        String normalized = error.replace("\n", " ").replace("\r", " ").trim();
        if (normalized.length() <= MAX_ERROR_TEXT) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_TEXT);
    }

    private void distributeCreditEvents(Map<String, EventGroup> grouped, PendingGroup pendingGroup) {
        List<Integer> bucketNos = balanceBucketSelectService.selectLowestTotalBalanceBucketNos(
                pendingGroup.userId,
                pendingGroup.currency,
                CREDIT_GROUP_BUCKET_COUNT);
        if (bucketNos == null || bucketNos.isEmpty()) {
            bucketNos = List.of((Integer) null);
        }
        List<EventGroup> bucketGroups = new ArrayList<>(bucketNos.size());
        String partitionSuffix = resolvePartitionSuffix(pendingGroup.createTime);
        for (Integer bucketNo : bucketNos) {
            String key = buildEventGroupKey(
                    pendingGroup.eventType,
                    pendingGroup.userId,
                    pendingGroup.currency,
                    bucketNo,
                    partitionSuffix);
            EventGroup group = grouped.computeIfAbsent(key, k -> new EventGroup(
                    pendingGroup.eventType,
                    pendingGroup.userId,
                    pendingGroup.currency,
                    buildRouteKey(pendingGroup.eventType, pendingGroup.userId, pendingGroup.currency, bucketNo),
                    bucketNo,
                    pendingGroup.createTime));
            bucketGroups.add(group);
        }
        pendingGroup.events.stream()
                .sorted(Comparator.comparing(AccountEventDto::getAmount).reversed())
                .forEach(event -> appendEventToGroup(selectLightestGroup(bucketGroups), event));
    }

    private EventGroup selectLightestGroup(List<EventGroup> groups) {
        return groups.stream()
                .min(Comparator.comparing(group -> group.totalAmount))
                .orElseThrow(() -> new IllegalStateException("no event group available"));
    }

    private void appendEventToGroup(EventGroup group, AccountEventDto event) {
        group.ids.add(event.getId());
        group.totalAmount = group.totalAmount.add(event.getAmount());
    }

    private boolean isCreditEvent(String eventType) {
        return AccountEventType.COLLECTION_CREDIT.getCode().equals(eventType)
                || AccountEventType.AGENT_CREDIT.getCode().equals(eventType);
    }

    private String buildEventGroupKey(String eventType, String userId, String currency, Integer bucketNo, String partitionSuffix) {
        return eventType + EVENT_KEY_SEPARATOR + userId + EVENT_KEY_SEPARATOR + currency
                + EVENT_KEY_SEPARATOR + partitionSuffix
                + EVENT_KEY_SEPARATOR + (bucketNo == null ? "cross" : bucketNo);
    }

    private String buildPendingGroupKey(String eventType, String userId, String currency, String partitionSuffix) {
        return eventType + EVENT_KEY_SEPARATOR + userId + EVENT_KEY_SEPARATOR + currency + EVENT_KEY_SEPARATOR + partitionSuffix;
    }

    private String buildRouteKey(String eventType, String userId, String currency, Integer bucketNo) {
        return eventType + EVENT_KEY_SEPARATOR + userId + EVENT_KEY_SEPARATOR + currency
                + EVENT_KEY_SEPARATOR + (bucketNo == null ? "cross" : bucketNo);
    }

    private String resolvePartitionSuffix(Long createTime) {
        return PARTITION_SUFFIX_FORMATTER.format(Instant.ofEpochSecond(createTime));
    }

    private long resolvePartitionStart(Long createTime) {
        LocalDateTime monthStart = Instant.ofEpochSecond(createTime)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay();
        return monthStart.toEpochSecond(ZoneOffset.UTC);
    }

    private long resolvePartitionEnd(Long createTime) {
        LocalDateTime monthEnd = Instant.ofEpochSecond(createTime)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay();
        return monthEnd.toEpochSecond(ZoneOffset.UTC);
    }

    private String resolveEventRouteKey(AccountEventDto event) {
        String routeKey = CommonUtil.resolveBalanceRouteKeyFromBizNo(event.getBizNo());
        if (routeKey != null && !routeKey.isBlank()) {
            return routeKey;
        }
        return String.valueOf(event.getId());
    }

    private BigDecimal resolveOrderAmount(BigDecimal actualAmount, BigDecimal amount) {
        return actualAmount != null ? actualAmount : amount;
    }

    private Long resolveEventCreatedAt(Long transactionCreateTime, Long fallbackTime) {
        if (transactionCreateTime != null && transactionCreateTime > 0) {
            return transactionCreateTime;
        }
        if (fallbackTime != null && fallbackTime > 0) {
            return fallbackTime;
        }
        return System.currentTimeMillis() / 1000;
    }

    private BucketSelectAction resolveBucketSelectAction(String eventType) {
        if (AccountEventType.COLLECTION_CREDIT.getCode().equals(eventType)
                || AccountEventType.AGENT_CREDIT.getCode().equals(eventType)) {
            return BucketSelectAction.CREDIT;
        }
        if (AccountEventType.PAYOUT_CONFIRM.getCode().equals(eventType)) {
            return BucketSelectAction.CONFIRM_PAYOUT;
        }
        if (AccountEventType.PAYOUT_RELEASE.getCode().equals(eventType)) {
            return BucketSelectAction.RELEASE_FROZEN;
        }
        throw new IllegalArgumentException("unsupported event type: " + eventType);
    }

    /**
     * One grouped bucket for a single balance operation.
     */
    private static class EventGroup {
        private final String eventType;
        private final String userId;
        private final String currency;
        private final String routeKey;
        private final Integer bucketNo;
        private final Long createTime;
        private final List<Long> ids = new ArrayList<>();
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private EventGroup(String eventType, String userId, String currency, String routeKey, Integer bucketNo, Long createTime) {
            this.eventType = eventType;
            this.userId = userId;
            this.currency = currency;
            this.routeKey = routeKey;
            this.bucketNo = bucketNo;
            this.createTime = createTime;
        }
    }

    private static class PendingGroup {
        private final String eventType;
        private final String userId;
        private final String currency;
        private final Long createTime;
        private final List<AccountEventDto> events = new ArrayList<>();
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private PendingGroup(String eventType, String userId, String currency, Long createTime) {
            this.eventType = eventType;
            this.userId = userId;
            this.currency = currency;
            this.createTime = createTime;
        }
    }

    private static class ClaimPartitionPlan {
        private final String tableName;
        private final int weight;

        private ClaimPartitionPlan(String tableName, int weight) {
            this.tableName = tableName;
            this.weight = weight;
        }
    }
}
