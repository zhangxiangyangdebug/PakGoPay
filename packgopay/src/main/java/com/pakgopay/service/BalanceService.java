package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BalanceService {

    CommonResponse fetchMerchantAvailableBalance(String userId, String authorization) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency) throws PakGoPayException;

    void releaseFrozenBalance(String userId, String currency, BigDecimal amount);

    void creditBalance(String userId, String currency, BigDecimal amount);

    void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper);

    void adjustBalance(String userId, String currency, BigDecimal amount);

    void confirmPayoutBalance(String userId, String currency, BigDecimal amount);

    BalanceUserInfo fetchBalanceSummaries(List<String> userId) throws PakGoPayException;

    void createBalanceRecord(String userId, String currency);
}
