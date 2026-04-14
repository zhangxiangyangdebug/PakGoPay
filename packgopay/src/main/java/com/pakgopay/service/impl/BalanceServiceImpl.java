package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.BalanceBucketMapper;
import com.pakgopay.mapper.dto.BalanceBucketDeltaDto;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.BalanceBucketSelectService;
import com.pakgopay.service.common.OrderBalanceSerializeExecutor;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BalanceServiceImpl implements BalanceService {

    private static final Logger BALANCE_LOG = LoggerFactory.getLogger("balance-change");

    @Autowired
    private BalanceBucketMapper balanceBucketMapper;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private BalanceBucketSelectService balanceBucketSelectService;

    @Autowired
    private OrderBalanceSerializeExecutor orderBalanceSerializeExecutor;

    @Override
    public CommonResponse fetchMerchantAvailableBalance(String userId, String authorization) throws PakGoPayException {
        List<BalanceDto> balanceDtoList = listUserBalances(userId);
        if (balanceDtoList == null || balanceDtoList.isEmpty()) {
            log.error("record is not exists, userId {}", userId);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_BALANCE_DATA);
        }
        Map<String, Map<String, BigDecimal>> allBalanceInfo = new HashMap<>();

        balanceDtoList.forEach(info -> {
            Map<String, BigDecimal> balanceInfo = new HashMap<>();
            balanceInfo.put("available", info.getAvailableBalance());
            balanceInfo.put("frozen", info.getFrozenBalance());
            balanceInfo.put("total", info.getTotalBalance());
            allBalanceInfo.put(info.getCurrency(), balanceInfo);
        });
        return CommonResponse.success(allBalanceInfo);
    }

    @Override
    public List<BalanceDto> listUserBalances(String userId) throws PakGoPayException {
        List<BalanceDto> balanceDtoList;
        try {
            balanceDtoList = balanceBucketMapper.findByUserId(userId);
        } catch (Exception e) {
            log.error("balance findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return balanceDtoList;
    }

    @Override
    public void freezeBalance(BigDecimal freezeFee, String userId, String currency, Integer bucketNo) throws PakGoPayException {
        executeWithAggregateUpdate(userId, currency, freezeFee, bucketNo, BucketOperation.ORDER_FREEZE);
    }

    @Override
    public void releaseFrozenBalance(String userId, String currency, BigDecimal amount, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.RELEASE_FROZEN);
    }

    @Override
    public void creditBalance(String userId, String currency, BigDecimal amount, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.CREDIT, true);
    }

    @Override
    public void creditBalanceWithoutSplit(String userId, String currency, BigDecimal amount, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.CREDIT, false);
    }

    private void upsertCreditBalanceBucket(String userId, String currency, BigDecimal amount, int bucketNo, long now) {
        int updated = balanceBucketMapper.upsertCreditBalance(userId, currency, bucketNo, amount, now);
        if (updated <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "upsert credit balance bucket failed");
        }
    }

    @Override
    public void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.fromWithdrawOper(oper), true);
    }

    @Override
    public void confirmPayoutBalance(String userId, String currency, BigDecimal amount, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.CONFIRM_PAYOUT, true);
    }

    @Override
    public void adjustBalance(String userId, String currency, BigDecimal amount, Integer bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.ADJUST, true);
    }

    private void executeWithAggregateUpdate(
            String userId, String currency, BigDecimal amount, Integer bucketNo, BucketOperation operation) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, operation, true);
    }

    private void executeWithAggregateUpdate(
            String userId, String currency, BigDecimal amount, Integer bucketNo, BucketOperation operation, boolean allowLargeSplit) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }
        try {
            long now = System.currentTimeMillis() / 1000;
            ensureBucketsIfRequired(userId, currency, amount, operation);
            Integer preferredBucketNo = resolvePreferredBucketNo(userId, currency, amount, bucketNo, operation, allowLargeSplit);
            List<BalanceBucketDeltaDto> appliedDeltas = applyBucketOperation(userId, currency, amount, preferredBucketNo, now, operation, allowLargeSplit);
            balanceBucketSelectService.applyBucketDeltas(userId, currency, appliedDeltas);
            BALANCE_LOG.info(
                    "action={} userId={} currency={} amount={} preferredBucketNo={} allowLargeSplit={} ts={}",
                    operation.name(), userId, currency, amount, preferredBucketNo, allowLargeSplit, now);
        } catch (PakGoPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} failed, message {}", operation.name(), e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
    }

    private void ensureBucketsIfRequired(String userId, String currency, BigDecimal amount, BucketOperation operation) {
        if (operation == BucketOperation.CREDIT
                || (operation == BucketOperation.ADJUST && amount != null && amount.compareTo(BigDecimal.ZERO) > 0)) {
            balanceBucketSelectService.ensureBucketsForCredit(userId, currency);
        }
    }

    private boolean checkParams(String userId, String currency, BigDecimal amount) {
        if (userId == null || userId.isEmpty()) {
            log.warn("userId is empty");
            return false;
        }

        if (currency == null || currency.isEmpty()) {
            log.warn("currency is empty");
            return false;
        }

        if (amount == null) {
            log.warn("amount is null");
            return false;
        }
        return true;
    }

    public BalanceUserInfo fetchBalanceSummaries(List<String> userIds) throws PakGoPayException {
        Map<String, Map<String, BigDecimal>> totalData = new HashMap<>();
        Map<String, Map<String, Map<String, BigDecimal>>> userDataMap = new HashMap<>();
        
        List<BalanceDto> balanceDtoList;
        try {
            balanceDtoList = balanceBucketMapper.listByUserIds(userIds);
        } catch (Exception e) {
            log.error("balance listByUserId failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (balanceDtoList == null || balanceDtoList.isEmpty()) {
            log.error("fetchBalanceSummaries record is not exists, userId {}", userIds);
            BalanceUserInfo empty = new BalanceUserInfo();
            empty.setTotalData(totalData);
            empty.setUserDataMap(userDataMap);
            return empty;
        }

        for (BalanceDto info : balanceDtoList) {
            String currency = info.getCurrency();
            String userId = info.getUserId();

            Map<String, BigDecimal> currencyMap =
                    totalData.computeIfAbsent(currency, k -> new HashMap<>());

            currencyMap.merge("total", amountDefaultValue(info.getTotalBalance()), BigDecimal::add);
            currencyMap.merge("available", amountDefaultValue(info.getAvailableBalance()), BigDecimal::add);
            currencyMap.merge("withdraw", amountDefaultValue(info.getWithdrawAmount()), BigDecimal::add);
            currencyMap.merge("frozen", amountDefaultValue(info.getFrozenBalance()), BigDecimal::add);


            Map<String, Map<String, BigDecimal>> userCurrencyMap =
                    userDataMap.computeIfAbsent(userId, k -> new HashMap<>());
            Map<String, BigDecimal> userBalance =
                    userCurrencyMap.computeIfAbsent(currency, k -> new HashMap<>());
            userBalance.put("total", amountDefaultValue(info.getTotalBalance()));
            userBalance.put("available", amountDefaultValue(info.getAvailableBalance()));
            userBalance.put("withdraw", amountDefaultValue(info.getWithdrawAmount()));
            userBalance.put("frozen", amountDefaultValue(info.getFrozenBalance()));
        }
        BalanceUserInfo info = new BalanceUserInfo();
        info.setTotalData(totalData);
        info.setUserDataMap(userDataMap);
        return info;
    }

    @Override
    public void createBalanceRecord(String userId, String currency) {
        try {
            long now = System.currentTimeMillis() / 1000;
            int ret = balanceBucketMapper.insertZeroBuckets(userId, currency, now, now);
            if (ret > 0) {
                balanceBucketSelectService.refreshBucketView(userId, currency);
                BALANCE_LOG.info(
                        "action=createBalanceRecord userId={} currency={} amount={} ts={}",
                        userId, currency, BigDecimal.ZERO, now);
            }
        } catch (Exception e) {
            log.error("createBalanceRecord failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
    }

    /**
     * Get balance snapshot; create zero-initialized balance row when absent.
     */
    @Override
    public BalanceDto loadOrCreateBalanceSnapshot(String userId, String currency) {
        BalanceDto dto = fetchBalanceSnapshot(userId, currency);
        if (dto != null) {
            return dto;
        }
        // Initialize balance row lazily for first-time user/currency.
        createBalanceRecord(userId, currency);
        BalanceDto created = fetchBalanceSnapshot(userId, currency);
        if (created == null) {
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR, "balance record not found");
        }
        return created;
    }

    private static BigDecimal amountDefaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BalanceDto fetchBalanceSnapshot(String userId, String currency) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return null;
        }
        try {
            return balanceBucketMapper.findByUserIdAndCurrency(userId, currency);
        } catch (Exception e) {
            log.error("fetchBalanceSnapshot failed, message {}", e.getMessage());
            return null;
        }
    }

    private List<BalanceBucketDeltaDto> applyBucketOperation(
            String userId, String currency, BigDecimal amount, Integer bucketNo, long now, BucketOperation operation, boolean allowLargeSplit) {
        if (bucketNo != null) {
            try {
                return applySingleBucketOperation(userId, currency, amount, bucketNo, now, operation);
            } catch (PakGoPayException e) {
                return applyCrossBucketOperationWithSerializeLock(userId, currency, amount, now, operation, allowLargeSplit);
            }
        }
        return applyCrossBucketOperationWithSerializeLock(userId, currency, amount, now, operation, allowLargeSplit);
    }

    @SuppressWarnings("unchecked")
    private List<BalanceBucketDeltaDto> applyCrossBucketOperationWithSerializeLock(
            String userId, String currency, BigDecimal amount, long now, BucketOperation operation, boolean allowLargeSplit) {
        List<BalanceBucketDeltaDto>[] resultHolder = new List[1];
        orderBalanceSerializeExecutor.run(
                userId + ":" + currency,
                () -> resultHolder[0] = transactionUtil.callInTransaction(
                        () -> applyCrossBucketOperation(userId, currency, amount, now, operation, allowLargeSplit)));
        return resultHolder[0];
    }

    private List<BalanceBucketDeltaDto> applySingleBucketOperation(
            String userId, String currency, BigDecimal amount, int bucketNo, long now, BucketOperation operation) {
        int bucketRet;
        List<BalanceBucketDeltaDto> deltas = new ArrayList<>(1);
        switch (operation) {
            case ORDER_FREEZE:
                bucketRet = balanceBucketMapper.freezeBalance(userId, amount, currency, bucketNo, now);
                break;
            case RELEASE_FROZEN:
                bucketRet = balanceBucketMapper.releaseFrozenBalance(userId, amount, currency, bucketNo, now);
                break;
            case CREDIT:
                upsertCreditBalanceBucket(userId, currency, amount, bucketNo, now);
                deltas.add(buildBucketDelta(bucketNo, amount, BigDecimal.ZERO, amount, BigDecimal.ZERO));
                return deltas;
            case ADJUST:
                bucketRet = balanceBucketMapper.adjustBalance(userId, amount, currency, bucketNo, now);
                break;
            case WITHDRAW_FREEZE:
                bucketRet = balanceBucketMapper.freezeForWithdraw(userId, amount, currency, bucketNo, now);
                break;
            case WITHDRAW_CONFIRM:
                bucketRet = balanceBucketMapper.confirmWithdraw(userId, amount, currency, bucketNo, now);
                break;
            case WITHDRAW_CANCEL:
                bucketRet = balanceBucketMapper.cancelWithdraw(userId, amount, currency, bucketNo, now);
                break;
            case CONFIRM_PAYOUT:
                bucketRet = balanceBucketMapper.confirmPayoutBalance(userId, amount, currency, bucketNo, now);
                break;
            default:
                throw new PakGoPayException(ResultCode.FAIL, "unsupported bucket operation");
        }
        if (bucketRet <= 0) {
            throw buildInsufficientException(operation);
        }
        deltas.add(buildBucketDelta(bucketNo, amount, operation));
        return deltas;
    }

    private List<BalanceBucketDeltaDto> applyCrossBucketOperation(
            String userId, String currency, BigDecimal amount, long now, BucketOperation operation, boolean allowLargeSplit) {
        List<BalanceDto> buckets = balanceBucketMapper.listBucketsForUpdate(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            throw new PakGoPayException(ResultCode.FAIL, "balance bucket not found");
        }
        if (allowLargeSplit && (operation == BucketOperation.CREDIT
                || (operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) > 0))
                && balanceBucketSelectService.shouldSplitLargeCredit(amount)) {
            List<BalanceBucketDeltaDto> deltas = balanceBucketSelectService.buildLargeCreditDeltas(userId, currency, amount);
            if (deltas.isEmpty()) {
                throw buildInsufficientException(operation);
            }
            int updated = balanceBucketMapper.batchApplyDeltas(userId, currency, now, deltas);
            if (updated != deltas.size()) {
                throw buildInsufficientException(operation);
            }
            return deltas;
        }
        if (operation == BucketOperation.CREDIT || (operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) > 0)) {
            int targetBucketNo = buckets.stream()
                    .min(Comparator.comparing(bucket -> amountDefaultValue(bucket.getTotalBalance())))
                    .map(BalanceDto::getBucketNo)
                    .orElse(0);
            List<BalanceBucketDeltaDto> deltas = new ArrayList<>(1);
            if (operation == BucketOperation.CREDIT) {
                deltas.add(buildBucketDelta(targetBucketNo, amount, BigDecimal.ZERO, amount, BigDecimal.ZERO));
            } else {
                deltas.add(buildBucketDelta(targetBucketNo, amount, BigDecimal.ZERO, amount, BigDecimal.ZERO));
            }
            int updated = balanceBucketMapper.batchApplyDeltas(userId, currency, now, deltas);
            if (updated != deltas.size()) {
                throw buildInsufficientException(operation);
            }
            return deltas;
        }

        BigDecimal remaining = operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) < 0
                ? amount.abs()
                : amount;
        List<BalanceBucketDeltaDto> deltas = new ArrayList<>();
        List<BalanceDto> orderedBuckets = switch (operation) {
            case ORDER_FREEZE, WITHDRAW_FREEZE, ADJUST ->
                    buckets.stream()
                            .sorted(Comparator.comparing((BalanceDto bucket) -> amountDefaultValue(bucket.getAvailableBalance()))
                                    .reversed())
                            .toList();
            case RELEASE_FROZEN, WITHDRAW_CANCEL, WITHDRAW_CONFIRM, CONFIRM_PAYOUT ->
                    buckets.stream()
                            .sorted(Comparator.comparing((BalanceDto bucket) -> amountDefaultValue(bucket.getFrozenBalance()))
                                    .reversed())
                            .toList();
            default -> buckets;
        };
        for (BalanceDto bucket : orderedBuckets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal capacity = switch (operation) {
                case ORDER_FREEZE, WITHDRAW_FREEZE ->
                        amountDefaultValue(bucket.getAvailableBalance());
                case ADJUST ->
                        amount.compareTo(BigDecimal.ZERO) < 0
                                ? amountDefaultValue(bucket.getAvailableBalance())
                                : BigDecimal.ZERO;
                case RELEASE_FROZEN, WITHDRAW_CANCEL, WITHDRAW_CONFIRM, CONFIRM_PAYOUT ->
                        amountDefaultValue(bucket.getFrozenBalance());
                default -> BigDecimal.ZERO;
            };
            if (capacity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applyAmount = remaining.min(capacity);
            BigDecimal deltaAmount = operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) < 0
                    ? applyAmount.negate()
                    : applyAmount;
            deltas.add(buildBucketDelta(bucket.getBucketNo(), deltaAmount, operation));
            remaining = remaining.subtract(applyAmount);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw buildInsufficientException(operation);
        }
        if (deltas.isEmpty()) {
            throw buildInsufficientException(operation);
        }
        int updated = balanceBucketMapper.batchApplyDeltas(userId, currency, now, deltas);
        if (updated != deltas.size()) {
            throw buildInsufficientException(operation);
        }
        return deltas;
    }

    private Integer resolvePreferredBucketNo(
            String userId, String currency, BigDecimal amount, Integer bucketNo, BucketOperation operation, boolean allowLargeSplit) {
        if (bucketNo != null) {
            return bucketNo;
        }
        if (allowLargeSplit && (operation == BucketOperation.CREDIT
                || (operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) > 0))
                && balanceBucketSelectService.shouldSplitLargeCredit(amount)) {
            return null;
        }
        return balanceBucketSelectService.selectBucketNo(
                userId, currency, amount, mapSelectAction(operation, amount));
    }

    private BalanceBucketDeltaDto buildBucketDelta(Integer bucketNo, BigDecimal amount, BucketOperation operation) {
        return switch (operation) {
            case ORDER_FREEZE, WITHDRAW_FREEZE ->
                    buildBucketDelta(bucketNo, amount.negate(), amount, BigDecimal.ZERO, BigDecimal.ZERO);
            case RELEASE_FROZEN, WITHDRAW_CANCEL ->
                    buildBucketDelta(bucketNo, amount, amount.negate(), BigDecimal.ZERO, BigDecimal.ZERO);
            case WITHDRAW_CONFIRM ->
                    buildBucketDelta(bucketNo, BigDecimal.ZERO, amount.negate(), BigDecimal.ZERO, amount);
            case CONFIRM_PAYOUT ->
                    buildBucketDelta(bucketNo, BigDecimal.ZERO, amount.negate(), amount.negate(), BigDecimal.ZERO);
            case ADJUST, CREDIT ->
                    buildBucketDelta(bucketNo, amount, BigDecimal.ZERO, amount, BigDecimal.ZERO);
        };
    }

    private BalanceBucketDeltaDto buildBucketDelta(
            Integer bucketNo,
            BigDecimal availableDelta,
            BigDecimal frozenDelta,
            BigDecimal totalDelta,
            BigDecimal withdrawDelta) {
        BalanceBucketDeltaDto dto = new BalanceBucketDeltaDto();
        dto.setBucketNo(bucketNo);
        dto.setAvailableDelta(availableDelta);
        dto.setFrozenDelta(frozenDelta);
        dto.setTotalDelta(totalDelta);
        dto.setWithdrawDelta(withdrawDelta);
        return dto;
    }

    private PakGoPayException buildInsufficientException(BucketOperation operation) {
        if (operation == BucketOperation.ORDER_FREEZE
                || operation == BucketOperation.WITHDRAW_FREEZE
                || operation == BucketOperation.ADJUST) {
            return new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH);
        }
        return new PakGoPayException(ResultCode.FAIL, operation.name() + " failed");
    }

    private BalanceBucketSelectService.BucketSelectAction mapSelectAction(BucketOperation operation, BigDecimal amount) {
        return switch (operation) {
            case ORDER_FREEZE -> BalanceBucketSelectService.BucketSelectAction.FREEZE;
            case RELEASE_FROZEN -> BalanceBucketSelectService.BucketSelectAction.RELEASE_FROZEN;
            case CREDIT -> BalanceBucketSelectService.BucketSelectAction.CREDIT;
            case ADJUST -> amount != null && amount.compareTo(BigDecimal.ZERO) < 0
                    ? BalanceBucketSelectService.BucketSelectAction.ADJUST_DECREASE
                    : BalanceBucketSelectService.BucketSelectAction.ADJUST_INCREASE;
            case WITHDRAW_FREEZE -> BalanceBucketSelectService.BucketSelectAction.WITHDRAW_FREEZE;
            case WITHDRAW_CONFIRM -> BalanceBucketSelectService.BucketSelectAction.WITHDRAW_CONFIRM;
            case WITHDRAW_CANCEL -> BalanceBucketSelectService.BucketSelectAction.WITHDRAW_CANCEL;
            case CONFIRM_PAYOUT -> BalanceBucketSelectService.BucketSelectAction.CONFIRM_PAYOUT;
        };
    }

    private enum BucketOperation {
        ORDER_FREEZE,
        RELEASE_FROZEN,
        CREDIT,
        ADJUST,
        WITHDRAW_FREEZE,
        WITHDRAW_CONFIRM,
        WITHDRAW_CANCEL,
        CONFIRM_PAYOUT;

        static BucketOperation fromWithdrawOper(Integer oper) {
            if (oper == null) {
                throw new PakGoPayException(ResultCode.FAIL, "withdraw oper is null");
            }
            if (oper == 0) {
                return WITHDRAW_FREEZE;
            }
            if (oper == 1) {
                return WITHDRAW_CONFIRM;
            }
            return WITHDRAW_CANCEL;
        }
    }

}
