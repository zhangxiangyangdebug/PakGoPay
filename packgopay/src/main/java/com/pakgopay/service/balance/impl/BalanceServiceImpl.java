package com.pakgopay.service.balance.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.BalanceMapper;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.balance.BalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BalanceServiceImpl implements BalanceService {

    @Autowired
    private BalanceMapper balanceMapper;

    @Override
    public CommonResponse queryMerchantAvailableBalance(String userId) throws PakGoPayException {

        BalanceDto balanceDto;
        try {
            balanceDto = balanceMapper.findByUserId(userId)
                            .orElseThrow(() -> new PakGoPayException(ResultCode.MERCHANT_HAS_NO_BALANCE_DATA));
        } catch (PakGoPayException e) {
            log.error("record is not exists, userId {}", userId);
            throw e;
        } catch (Exception e) {
            log.error("balance findByTransactionNo failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        Map<String, BigDecimal> balanceInfo = new HashMap<>();
        balanceInfo.put("available", balanceDto.getAvailableBalance());
        balanceInfo.put("frozen", balanceDto.getFrozenBalance());

        return CommonResponse.success(balanceInfo);
    }
}
