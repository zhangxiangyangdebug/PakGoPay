package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.dto.BalanceDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BalanceService {

    CommonResponse fetchMerchantAvailableBalance(String userId, String authorization) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency, int bucketNo) throws PakGoPayException;

    void releaseFrozenBalance(String userId, String currency, BigDecimal amount);

    void releaseFrozenBalance(String userId, String currency, BigDecimal amount, int bucketNo);

    void creditBalance(String userId, String currency, BigDecimal amount);

    void creditBalance(String userId, String currency, BigDecimal amount, int bucketNo);

    void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper);

    void applyWithdrawalOperation(String userId, String currency, BigDecimal amount, Integer oper, int bucketNo);

    void adjustBalance(String userId, String currency, BigDecimal amount);

    void adjustBalance(String userId, String currency, BigDecimal amount, int bucketNo);

    void confirmPayoutBalance(String userId, String currency, BigDecimal amount);

    void confirmPayoutBalance(String userId, String currency, BigDecimal amount, int bucketNo);

    /**
     * Query balance snapshot, create zero balance row when absent, then return latest snapshot.
     */
    BalanceDto loadOrCreateBalanceSnapshot(String userId, String currency);

    BalanceUserInfo fetchBalanceSummaries(List<String> userId) throws PakGoPayException;

    void createBalanceRecord(String userId, String currency);
}
