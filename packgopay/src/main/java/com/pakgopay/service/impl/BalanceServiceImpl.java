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
import org.springframework.transaction.annotation.Transactional;

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
    public CommonResponse queryMerchantAvailableBalance(String userId) throws PakGoPayException {
        log.info("queryMerchantAvailableBalance start");
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
        log.info("queryMerchantAvailableBalance end");
        return CommonResponse.success(allBalanceInfo);
    }

    @Transactional
    @Override
    public void freezeBalance(BigDecimal freezeFee, String userId, String currency) throws PakGoPayException {
        log.info("freezeBalance start");
        BalanceDto balanceDto;
        try {
            balanceDto = balanceMapper.findByUserIdAndCurrency(userId, currency);
        } catch (Exception e) {
            log.error("balance findByUserIdAndCurrency failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (freezeFee.compareTo(balanceDto.getAvailableBalance()) <= CommonConstant.ZERO) {
            log.info("merchants with insufficient available balance, userId: {} currency: {}", userId, currency);
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
        log.info("freezeBalance end");
    }

    public Map<String, Map<String, BigDecimal>> getBalanceInfos(List<String> userIds) throws PakGoPayException {
        log.info("getBalanceInfos start");
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();
        List<BalanceDto> balanceDtoList;
        try {
            balanceDtoList = balanceMapper.listByUserIds(userIds);
        } catch (Exception e) {
            log.error("balance listByUserId failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (balanceDtoList == null || balanceDtoList.isEmpty()) {
            log.error("getBalanceInfos record is not exists, userId {}", userIds);
            return result;
        }

        for (BalanceDto info : balanceDtoList) {
            String currency = info.getCurrency();

            Map<String, BigDecimal> currencyMap =
                    result.computeIfAbsent(currency, k -> new HashMap<>());

            currencyMap.merge("total", amountDefaultValue(info.getTotalBalance()), BigDecimal::add);
            currencyMap.merge("withdraw", amountDefaultValue(info.getWithdrawAmount()), BigDecimal::add);
            currencyMap.merge("frozen", amountDefaultValue(info.getFrozenBalance()), BigDecimal::add);
        }
        log.info("getBalanceInfos end");
        return result;
    }

    private static BigDecimal amountDefaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
