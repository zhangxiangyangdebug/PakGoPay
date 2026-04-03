package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.BalanceBucketMapper;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BalanceServiceImpl implements BalanceService {

    private static final Logger BALANCE_LOG = LoggerFactory.getLogger("balance-change");

    @Autowired
    private BalanceMapper balanceMapper;

    @Autowired
    private BalanceBucketMapper balanceBucketMapper;

    @Autowired
    private TransactionUtil transactionUtil;

    @Override
    public CommonResponse fetchMerchantAvailableBalance(String userId, String authorization) throws PakGoPayException {
        List<BalanceDto> balanceDtoList;
        try {
            balanceDtoList = balanceMapper.findByUserId(userId);
        } catch (Exception e) {
            log.error("balance findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

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
    public void freezeBalance(BigDecimal freezeFee, String userId, String currency) throws PakGoPayException {
        executeWithAggregateUpdate(userId, currency, freezeFee, null, BucketOperation.ORDER_FREEZE);
    }

    @Override
    public void freezeBalance(BigDecimal freezeFee, String userId, String currency, int bucketNo) throws PakGoPayException {
        executeWithAggregateUpdate(userId, currency, freezeFee, bucketNo, BucketOperation.ORDER_FREEZE);
    }

    @Override
    public void releaseFrozenBalance(String userId, String currency, BigDecimal amount) {
        executeWithAggregateUpdate(userId, currency, amount, null, BucketOperation.RELEASE_FROZEN);
    }

    @Override
    public void releaseFrozenBalance(String userId, String currency, BigDecimal amount, int bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.RELEASE_FROZEN);
    }

    @Override
    public void creditBalance(String userId, String currency, BigDecimal amount) {
        executeWithAggregateUpdate(userId, currency, amount, null, BucketOperation.CREDIT);
    }

    @Override
    public void creditBalance(String userId, String currency, BigDecimal amount, int bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.CREDIT);
    }

    private void upsertCreditBalance(String userId, String currency, BigDecimal amount, long now) {
        int updated = balanceMapper.upsertCreditBalance(userId, currency, amount, now);
        if (updated <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "upsert credit balance failed");
        }
    }

    private void upsertCreditBalanceBucket(String userId, String currency, BigDecimal amount, int bucketNo, long now) {
        int updated = balanceBucketMapper.upsertCreditBalance(userId, currency, bucketNo, amount, now);
        if (updated <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "upsert credit balance bucket failed");
        }
    }

    @Override
    public void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper) {
        executeWithAggregateUpdate(userId, currency, amount, null, BucketOperation.fromWithdrawOper(oper));
    }

    @Override
    public void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper, int bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.fromWithdrawOper(oper));
    }

    @Override
    public void confirmPayoutBalance(String userId, String currency, BigDecimal amount) {
        executeWithAggregateUpdate(userId, currency, amount, null, BucketOperation.CONFIRM_PAYOUT);
    }

    @Override
    public void confirmPayoutBalance(String userId, String currency, BigDecimal amount, int bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.CONFIRM_PAYOUT);
    }

    @Override
    public void adjustBalance(String userId, String currency, BigDecimal amount) {
        executeWithAggregateUpdate(userId, currency, amount, null, BucketOperation.ADJUST);
    }

    @Override
    public void adjustBalance(String userId, String currency, BigDecimal amount, int bucketNo) {
        executeWithAggregateUpdate(userId, currency, amount, bucketNo, BucketOperation.ADJUST);
    }

    private void executeWithAggregateUpdate(
            String userId, String currency, BigDecimal amount, Integer bucketNo, BucketOperation operation) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }
        try {
            transactionUtil.runInTransaction(() -> {
                long now = System.currentTimeMillis() / 1000;
                ensureZeroBuckets(userId, currency, now);
                applyBucketOperation(userId, currency, amount, bucketNo, now, operation);
                applyAggregateOperation(userId, currency, amount, now, operation);
                BALANCE_LOG.info(
                        "action={} userId={} currency={} amount={} bucketNo={} ts={}",
                        operation.name(), userId, currency, amount, bucketNo, now);
            });
        } catch (PakGoPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} failed, message {}", operation.name(), e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
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
            balanceDtoList = balanceMapper.listByUserIds(userIds);
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
            BalanceDto dto = new BalanceDto();
            dto.setUserId(userId);
            dto.setCurrency(currency);
            dto.setAvailableBalance(BigDecimal.ZERO);
            dto.setFrozenBalance(BigDecimal.ZERO);
            dto.setTotalBalance(BigDecimal.ZERO);
            long now = System.currentTimeMillis() / 1000;
            dto.setCreateTime(now);
            dto.setUpdateTime(now);

            int ret = balanceMapper.insert(dto);
            ensureZeroBuckets(userId, currency, now);
            if (ret > 0) {
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
            return balanceMapper.findByUserIdAndCurrency(userId, currency);
        } catch (Exception e) {
            log.error("fetchBalanceSnapshot failed, message {}", e.getMessage());
            return null;
        }
    }

    private void ensureZeroBuckets(String userId, String currency, long now) {
        balanceBucketMapper.insertZeroBuckets(userId, currency, now, now);
    }

    private void applyBucketOperation(
            String userId, String currency, BigDecimal amount, Integer bucketNo, long now, BucketOperation operation) {
        if (bucketNo != null) {
            applySingleBucketOperation(userId, currency, amount, bucketNo, now, operation);
            return;
        }
        applyCrossBucketOperation(userId, currency, amount, now, operation);
    }

    private void applyAggregateOperation(
            String userId, String currency, BigDecimal amount, long now, BucketOperation operation) {
        int ret;
        switch (operation) {
            case ORDER_FREEZE:
                ret = balanceMapper.freezeBalance(userId, amount, currency, now);
                break;
            case RELEASE_FROZEN:
                ret = balanceMapper.releaseFrozenBalance(userId, amount, currency, now);
                break;
            case CREDIT:
                upsertCreditBalance(userId, currency, amount, now);
                return;
            case ADJUST:
                ret = balanceMapper.adjustBalance(userId, amount, currency, now);
                break;
            case WITHDRAW_FREEZE:
                ret = balanceMapper.freezeForWithdraw(userId, amount, currency, now);
                break;
            case WITHDRAW_CONFIRM:
                ret = balanceMapper.confirmWithdraw(userId, amount, currency, now);
                break;
            case WITHDRAW_CANCEL:
                ret = balanceMapper.cancelWithdraw(userId, amount, currency, now);
                break;
            case CONFIRM_PAYOUT:
                ret = balanceMapper.confirmPayoutBalance(userId, amount, currency, now);
                break;
            default:
                throw new PakGoPayException(ResultCode.FAIL, "unsupported aggregate operation");
        }
        if (ret <= 0) {
            throw buildInsufficientException(operation);
        }
    }

    private void applySingleBucketOperation(
            String userId, String currency, BigDecimal amount, int bucketNo, long now, BucketOperation operation) {
        int bucketRet;
        switch (operation) {
            case ORDER_FREEZE:
                bucketRet = balanceBucketMapper.freezeBalance(userId, amount, currency, bucketNo, now);
                break;
            case RELEASE_FROZEN:
                bucketRet = balanceBucketMapper.releaseFrozenBalance(userId, amount, currency, bucketNo, now);
                break;
            case CREDIT:
                upsertCreditBalanceBucket(userId, currency, amount, bucketNo, now);
                return;
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
    }

    private void applyCrossBucketOperation(
            String userId, String currency, BigDecimal amount, long now, BucketOperation operation) {
        List<BalanceDto> buckets = balanceBucketMapper.listBucketsForUpdate(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            throw new PakGoPayException(ResultCode.FAIL, "balance bucket not found");
        }
        if (operation == BucketOperation.CREDIT || (operation == BucketOperation.ADJUST && amount.compareTo(BigDecimal.ZERO) > 0)) {
            int targetBucketNo = buckets.stream()
                    .min(Comparator.comparing(bucket -> amountDefaultValue(bucket.getTotalBalance())))
                    .map(BalanceDto::getBucketNo)
                    .orElse(0);
            if (operation == BucketOperation.CREDIT) {
                upsertCreditBalanceBucket(userId, currency, amount, targetBucketNo, now);
            } else {
                int updated = balanceBucketMapper.adjustBalance(userId, amount, currency, targetBucketNo, now);
                if (updated <= 0) {
                    throw buildInsufficientException(operation);
                }
            }
            return;
        }

        BigDecimal remaining = amount;
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
            applySingleBucketOperation(userId, currency, applyAmount, bucket.getBucketNo(), now, operation);
            remaining = remaining.subtract(applyAmount);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw buildInsufficientException(operation);
        }
    }

    private PakGoPayException buildInsufficientException(BucketOperation operation) {
        if (operation == BucketOperation.ORDER_FREEZE
                || operation == BucketOperation.WITHDRAW_FREEZE
                || operation == BucketOperation.ADJUST) {
            return new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH);
        }
        return new PakGoPayException(ResultCode.FAIL, operation.name() + " failed");
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
