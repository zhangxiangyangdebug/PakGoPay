package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.util.CommontUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BalanceServiceImpl implements BalanceService {

    @Autowired
    private BalanceMapper balanceMapper;

    @Override
    public CommonResponse fetchMerchantAvailableBalance(String userId) throws PakGoPayException {
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
        BalanceDto balanceDto;
        try {
            balanceDto = balanceMapper.findByUserIdAndCurrency(userId, currency);
        } catch (Exception e) {
            log.error("balance findByUserIdAndCurrency failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (freezeFee.compareTo(balanceDto.getAvailableBalance()) <= CommonConstant.ZERO) {
            log.warn("insufficient available balance, userId: {} currency: {}", userId, currency);
            throw new PakGoPayException(ResultCode.MERCHANT_BALANCE_NOT_ENOUGH);
        }

        BalanceDto updateInfo = new BalanceDto();
        updateInfo.setUserId(balanceDto.getUserId());
        updateInfo.setAvailableBalance(CommontUtil.safeSubtract(balanceDto.getAvailableBalance(), freezeFee));
        updateInfo.setFrozenBalance(CommontUtil.safeAdd(balanceDto.getFrozenBalance(), freezeFee));
        updateInfo.setUpdateTime(Instant.now().getEpochSecond());

        try {
            balanceMapper.updateByUserId(updateInfo);
        } catch (Exception e) {
            log.error("balance updateByUserId failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
    }

    @Override
    public void creditBalance(String userId, String currency, BigDecimal amount) {
        if (!checkParams(userId, currency, amount)) {
            return;
        }

        int ret = 0;
        try {
            long now = System.currentTimeMillis() / 1000;
            ret = balanceMapper.addAvailableBalance(userId, amount, currency, now);
        } catch (Exception e) {
            log.error("creditBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        if (ret <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "addAvailableBalance failed");
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
        } catch (Exception e) {
            log.error("applyWithdrawalOperation failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (ret <= 0) {
            throw new PakGoPayException(ResultCode.FAIL, "withdrawBalance failed");
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
            ret = balanceMapper.adjustBalance(userId, amount, currency, now);
        } catch (Exception e) {
            log.error("adjustBalance failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (ret <= 0) {
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

    public Map<String, Map<String, BigDecimal>> fetchBalanceSummaries(List<String> userIds) throws PakGoPayException {
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();

        List<BalanceDto> balanceDtoList;
        try {
            balanceDtoList = balanceMapper.listByUserIds(userIds);
        } catch (Exception e) {
            log.error("balance listByUserId failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (balanceDtoList == null || balanceDtoList.isEmpty()) {
            log.error("fetchBalanceSummaries record is not exists, userId {}", userIds);
            return result;
        }

        for (BalanceDto info : balanceDtoList) {
            String currency = info.getCurrency();

            Map<String, BigDecimal> currencyMap =
                    result.computeIfAbsent(currency, k -> new HashMap<>());

            currencyMap.merge("total", amountDefaultValue(info.getTotalBalance()), BigDecimal::add);
            currencyMap.merge("available", amountDefaultValue(info.getAvailableBalance()), BigDecimal::add);
            currencyMap.merge("withdraw", amountDefaultValue(info.getWithdrawAmount()), BigDecimal::add);
            currencyMap.merge("frozen", amountDefaultValue(info.getFrozenBalance()), BigDecimal::add);
        }
        return result;
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
        } catch (Exception e) {
            log.error("createBalanceRecord failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
    }

    private static BigDecimal amountDefaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
