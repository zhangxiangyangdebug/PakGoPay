package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.BalanceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BalanceServiceImpl implements BalanceService {

    private static final Logger BALANCE_LOG = LoggerFactory.getLogger("balance-change");

    @Autowired
    private BalanceMapper balanceMapper;

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
        BalanceDto before = fetchBalanceSnapshot(userId, currency);
        try {
            long now = System.currentTimeMillis() / 1000;
            int ret = balanceMapper.freezeBalance(userId, freezeFee, currency, now);
            if (ret <= 0) {
                log.warn("insufficient available balance, userId: {} currency: {}", userId, currency);
                throw new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH);
            }
        } catch (PakGoPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("balance freezeBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        BalanceDto after = fetchBalanceSnapshot(userId, currency);
        logBalanceChange("freezeBalance", before, after, freezeFee, userId, currency);
    }

    @Override
    public void releaseFrozenBalance(String userId, String currency, BigDecimal amount) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }
        BalanceDto before = fetchBalanceSnapshot(userId, currency);
        try {
            long now = System.currentTimeMillis() / 1000;
            int ret = balanceMapper.releaseFrozenBalance(userId, amount, currency, now);
            if (ret <= 0) {
                throw new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH, "frozen balance is not enough");
            }
        } catch (Exception e) {
            log.error("releaseFrozenBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        BalanceDto after = fetchBalanceSnapshot(userId, currency);
        logBalanceChange("releaseFrozenBalance", before, after, amount, userId, currency);
    }

    @Override
    public void creditBalance(String userId, String currency, BigDecimal amount) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("amount is negative, userId: {} currency: {}", userId, currency);
            return;
        }

        try {
            long now = System.currentTimeMillis() / 1000;
            BalanceDto before = fetchBalanceSnapshot(userId, currency);
            upsertCreditBalance(userId, currency, amount, now);
            BalanceDto after = fetchBalanceSnapshot(userId, currency);
            logBalanceChange("creditBalance", before, after, amount, userId, currency);
        } catch (Exception e) {
            log.error("creditBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
    }

    private void upsertCreditBalance(String userId, String currency, BigDecimal amount, long now) {
        int updated = balanceMapper.addAvailableBalance(userId, amount, currency, now);
        if (updated > 0) {
            return;
        }

        BalanceDto dto = new BalanceDto();
        dto.setUserId(userId);
        dto.setCurrency(currency);
        dto.setAvailableBalance(amount);
        dto.setFrozenBalance(BigDecimal.ZERO);
        dto.setWithdrawAmount(BigDecimal.ZERO);
        dto.setTotalBalance(amount);
        dto.setCreateTime(now);
        dto.setUpdateTime(now);

        try {
            int insert = balanceMapper.insert(dto);
            if (insert <= 0) {
                throw new PakGoPayException(ResultCode.FAIL, "insert balance failed");
            }
        } catch (Exception e) {
            // If another thread inserted, retry update.
            int retry = balanceMapper.addAvailableBalance(userId, amount, currency, now);
            if (retry <= 0) {
                throw e;
            }
        }
    }

    @Override
    public void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }

        int ret = 0;
        try {
            long now = System.currentTimeMillis() / 1000;
            BalanceDto before = fetchBalanceSnapshot(userId, currency);
            if (oper == 0) {
                // freeze
                ret = balanceMapper.freezeForWithdraw(userId, amount, currency, now);
            } else if (oper == 1) {
                // confirm
                ret = balanceMapper.confirmWithdraw(userId, amount, currency, now);
            } else {
                // cancel
                ret = balanceMapper.cancelWithdraw(userId, amount, currency, now);
            }
            BalanceDto after = fetchBalanceSnapshot(userId, currency);
            logBalanceChange("applyWithdrawalOperation(" + oper + ")", before, after, amount, userId, currency);
        } catch (Exception e) {
            log.error("applyWithdrawalOperation failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (ret <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "withdrawBalance failed");
        }
    }

    public void confirmPayoutBalance(String userId, String currency, BigDecimal amount) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }
        int ret = 0;
        try {
            long now = System.currentTimeMillis() / 1000;
            BalanceDto before = fetchBalanceSnapshot(userId, currency);
            ret = balanceMapper.confirmPayoutBalance(userId, amount, currency, now);
            BalanceDto after = fetchBalanceSnapshot(userId, currency);
            logBalanceChange("confirmPayoutBalance", before, after, amount, userId, currency);
        } catch (Exception e) {
            log.error("confirmPayoutBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        if (ret <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "confirmPayoutBalance failed");
        }
    }

    @Override
    public void adjustBalance(String userId, String currency, BigDecimal amount) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }

        int ret = 0;
        try {
            long now = System.currentTimeMillis() / 1000;
            BalanceDto before = fetchBalanceSnapshot(userId, currency);
            ret = balanceMapper.adjustBalance(userId, amount, currency, now);
            BalanceDto after = fetchBalanceSnapshot(userId, currency);
            logBalanceChange("adjustBalance", before, after, amount, userId, currency);
        } catch (Exception e) {
            log.error("adjustBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (ret <= 0) {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH);
            }
            throw new PakGoPayException(ResultCode.FAIL, "adjustBalance failed");
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
            BalanceDto before = fetchBalanceSnapshot(userId, currency);
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
            BalanceDto after = fetchBalanceSnapshot(userId, currency);
            logBalanceChange("createBalanceRecord", before, after, BigDecimal.ZERO, userId, currency);
        } catch (Exception e) {
            log.error("createBalanceRecord failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
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

    private void logBalanceChange(String operation,
                                  BalanceDto before,
                                  BalanceDto after,
                                  BigDecimal amount,
                                  String userId,
                                  String currency) {
        String source = MDC.get("balanceSource");
        String transactionNo = MDC.get("balanceTransactionNo");
        String op = operation;
        if (source != null && !source.isBlank()) {
            op = source + ":" + operation;
        }
        BigDecimal beforeAvailable = before == null ? BigDecimal.ZERO : amountDefaultValue(before.getAvailableBalance());
        BigDecimal beforeFrozen = before == null ? BigDecimal.ZERO : amountDefaultValue(before.getFrozenBalance());
        BigDecimal beforeTotal = before == null ? BigDecimal.ZERO : amountDefaultValue(before.getTotalBalance());
        BigDecimal beforeWithdraw = before == null ? BigDecimal.ZERO : amountDefaultValue(before.getWithdrawAmount());

        BigDecimal afterAvailable = after == null ? BigDecimal.ZERO : amountDefaultValue(after.getAvailableBalance());
        BigDecimal afterFrozen = after == null ? BigDecimal.ZERO : amountDefaultValue(after.getFrozenBalance());
        BigDecimal afterTotal = after == null ? BigDecimal.ZERO : amountDefaultValue(after.getTotalBalance());
        BigDecimal afterWithdraw = after == null ? BigDecimal.ZERO : amountDefaultValue(after.getWithdrawAmount());

        BigDecimal deltaAvailable = afterAvailable.subtract(beforeAvailable);
        BigDecimal deltaFrozen = afterFrozen.subtract(beforeFrozen);
        BigDecimal deltaTotal = afterTotal.subtract(beforeTotal);
        BigDecimal deltaWithdraw = afterWithdraw.subtract(beforeWithdraw);

        BALANCE_LOG.info("balance op={}, source={}, transactionNo={}, userId={}, currency={}, amount={}\n" +
                        "before[available={}, frozen={}, total={}, withdraw={}]\n" +
                        "after[available={}, frozen={}, total={}, withdraw={}]\n" +
                        "delta[available={}, frozen={}, total={}, withdraw={}]",
                op, source, transactionNo, userId, currency, amountDefaultValue(amount),
                beforeAvailable, beforeFrozen, beforeTotal, beforeWithdraw,
                afterAvailable, afterFrozen, afterTotal, afterWithdraw,
                deltaAvailable, deltaFrozen, deltaTotal, deltaWithdraw);
    }
}
