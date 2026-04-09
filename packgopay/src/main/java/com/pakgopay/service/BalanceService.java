package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.BalanceUserInfo;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.dto.BalanceDto;

import java.math.BigDecimal;
import java.util.List;

public interface BalanceService {

    CommonResponse fetchMerchantAvailableBalance(String userId, String authorization) throws PakGoPayException;

    List<BalanceDto> listUserBalances(String userId) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency, Integer bucketNo) throws PakGoPayException;

    void releaseFrozenBalance(String userId, String currency, BigDecimal amount, Integer bucketNo);

    void creditBalance(String userId, String currency, BigDecimal amount, Integer bucketNo);

    void creditBalanceWithoutSplit(String userId, String currency, BigDecimal amount, Integer bucketNo);

    void applyWithdrawalOperation(
            String userId, String currency, BigDecimal amount, Integer oper, Integer bucketNo);

    void adjustBalance(String userId, String currency, BigDecimal amount, Integer bucketNo);

    void confirmPayoutBalance(String userId, String currency, BigDecimal amount, Integer bucketNo);

    /**
     * Query balance snapshot, create zero balance row when absent, then return latest snapshot.
     */
    BalanceDto loadOrCreateBalanceSnapshot(String userId, String currency);

    BalanceUserInfo fetchBalanceSummaries(List<String> userId) throws PakGoPayException;

    void createBalanceRecord(String userId, String currency);
}
