package com.pakgopay.service.common;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.AccountEventStatus;
import com.pakgopay.common.enums.AccountEventType;
import com.pakgopay.mapper.AccountEventMapper;
import com.pakgopay.mapper.secondary.BalanceChangeLogMapper;
import com.pakgopay.mapper.dto.AccountEventDto;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.BalanceChangeLogDto;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.util.CalcUtil;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    @Autowired
    private AccountEventMapper accountEventMapper;

    @Autowired
    private BalanceChangeLogMapper secondaryBalanceChangeLogMapper;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private OrderBalanceSerializeExecutor orderBalanceSerializeExecutor;

    @Autowired
    private TransactionUtil transactionUtil;

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
                        : collectionOrderDto.getSuccessCallbackTime());

        appendAgentCreditEvents(
                eventsToAppend,
                collectionOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                collectionOrderDto.getCurrencyType(),
                collectionOrderDto.getAgent1Fee(),
                collectionOrderDto.getAgent2Fee(),
                collectionOrderDto.getAgent3Fee());
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
        List<AccountEventDto> eventsToAppend = new ArrayList<>();
        appendEvent(eventsToAppend,
                AccountEventType.PAYOUT_CONFIRM,
                payOrderDto.getTransactionNo() + ":MCH_CONFIRM",
                payOrderDto.getMerchantUserId(),
                payOrderDto.getCurrencyType(),
                frozenAmount,
                payOrderDto.getSuccessCallbackTime() == null
                        ? payOrderDto.getUpdateTime()
                        : payOrderDto.getSuccessCallbackTime());

        appendAgentCreditEvents(
                eventsToAppend,
                payOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                payOrderDto.getCurrencyType(),
                payOrderDto.getAgent1Fee(),
                payOrderDto.getAgent2Fee(),
                payOrderDto.getAgent3Fee());
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
        List<AccountEventDto> eventsToAppend = new ArrayList<>();
        appendEvent(eventsToAppend,
                AccountEventType.PAYOUT_RELEASE,
                payOrderDto.getTransactionNo() + ":MCH_RELEASE",
                payOrderDto.getMerchantUserId(),
                payOrderDto.getCurrencyType(),
                frozenAmount,
                payOrderDto.getUpdateTime());
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
        for (int i = 0; i < rounds; i++) {
            long now = System.currentTimeMillis() / 1000;
            List<AccountEventDto> claimed = accountEventMapper.claimPendingEvents(AccountEventType.allCodes(), claimLimit, now);
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            totalConsumed += claimed.size();
            processClaimedEventBatch(claimed, now);
        }
        return totalConsumed;
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
        String batchNo = buildBatchNo(group.eventType, group.userId, group.currency);
        try {
            transactionUtil.runInTransaction(() -> {
                applyBalanceChange(group, batchNo);
                accountEventMapper.markDoneByIds(group.ids, batchNo, now);
            });
            writeBalanceChangeLog(group, batchNo, now);
            log.info("account event batch done, batchNo={}, eventType={}, userId={}, currency={}, count={}, amount={}",
                    batchNo, group.eventType, group.userId, group.currency, group.ids.size(), group.totalAmount);
        } catch (Exception e) {
            String error = trimError(e == null ? null : e.getMessage());
            accountEventMapper.markFailedByIds(group.ids, error, now);
            log.warn("account event batch failed, batchNo={}, eventType={}, userId={}, currency={}, count={}, error={}",
                    batchNo, group.eventType, group.userId, group.currency, group.ids.size(), error);
        }
    }

    /**
     * Map grouped event type to balance operation.
     */
    private void applyBalanceChange(EventGroup group, String batchNo) {
        String serializeKey = buildOrderBalanceSerializeKey(group.userId, group.currency, group.bucketNo);
        orderBalanceSerializeExecutor.run(serializeKey, () ->
                CommonUtil.withBalanceLogContext("account_event.consume", group.routeKey, () -> {
                    if (AccountEventType.COLLECTION_CREDIT.getCode().equals(group.eventType)
                            || AccountEventType.AGENT_CREDIT.getCode().equals(group.eventType)) {
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
                }));
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
     * Group claim rows by (eventType,userId,currency), and sum amount for each group.
     */
    private Map<String, EventGroup> groupEvents(List<AccountEventDto> events) {
        Map<String, EventGroup> grouped = new LinkedHashMap<>();
        for (AccountEventDto event : events) {
            if (event == null
                    || event.getId() == null
                    || event.getAmount() == null
                    || event.getEventType() == null
                    || event.getUserId() == null
                    || event.getCurrency() == null
                    || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String routeKey = resolveEventRouteKey(event);
            int bucketNo = CommonUtil.resolveBalanceBucketNo(routeKey, 16);
            String key = buildEventGroupKey(event.getEventType(), event.getUserId(), event.getCurrency(), bucketNo);
            EventGroup group = grouped.computeIfAbsent(key, k -> new EventGroup(
                    event.getEventType(), event.getUserId(), event.getCurrency(), routeKey, bucketNo));
            group.ids.add(event.getId());
            group.totalAmount = group.totalAmount.add(event.getAmount());
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
            BigDecimal agent3Fee) {
        if (agentInfos == null || agentInfos.isEmpty()) {
            return;
        }
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_FIRST, agent1Fee, currency);
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_SECOND, agent2Fee, currency);
        appendOneAgentFee(eventsToAppend, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_THIRD, agent3Fee, currency);
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
            String currency) {
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
                        System.currentTimeMillis() / 1000);
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
            Long eventTime) {
        if (eventType == null || userId == null || userId.isBlank()
                || currency == null || currency.isBlank()
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        AccountEventDto dto = new AccountEventDto();
        dto.setEventType(eventType.getCode());
        dto.setBizNo(bizNo);
        dto.setUserId(userId);
        dto.setCurrency(currency);
        dto.setAmount(amount);
        dto.setEventTime(eventTime == null ? now : eventTime);
        dto.setStatus(AccountEventStatus.PENDING.getCode());
        dto.setRetryCount(0);
        dto.setCreatedAt(now);
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

    private String buildEventGroupKey(String eventType, String userId, String currency, int bucketNo) {
        return eventType + EVENT_KEY_SEPARATOR + userId + EVENT_KEY_SEPARATOR + currency
                + EVENT_KEY_SEPARATOR + bucketNo;
    }

    private String buildOrderBalanceSerializeKey(String userId, String currency, int bucketNo) {
        return userId + ":" + currency + ":" + bucketNo;
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

    /**
     * One grouped bucket for a single balance operation.
     */
    private static class EventGroup {
        private final String eventType;
        private final String userId;
        private final String currency;
        private final String routeKey;
        private final int bucketNo;
        private final List<Long> ids = new ArrayList<>();
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private EventGroup(String eventType, String userId, String currency, String routeKey, int bucketNo) {
            this.eventType = eventType;
            this.userId = userId;
            this.currency = currency;
            this.routeKey = routeKey;
            this.bucketNo = bucketNo;
        }
    }
}
