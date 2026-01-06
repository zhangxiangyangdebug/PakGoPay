package com.pakgopay.service.balance.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.balance.BalanceService;
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

    @Transactional
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

    }
}
