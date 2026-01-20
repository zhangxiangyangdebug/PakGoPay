package com.pakgopay.service;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.response.CommonResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BalanceService {

    CommonResponse queryMerchantAvailableBalance(String userId) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency) throws PakGoPayException;

    void rechargeAmount(String userId, String currency, BigDecimal amount);

    void withdrawAmount(String userId, String currency, BigDecimal amount, Integer oper);

    void adjustAmount(String userId, String currency, BigDecimal amount);

    Map<String, Map<String, BigDecimal>> getBalanceInfos(List<String> userId) throws PakGoPayException;

    void createBalanceRecord(String userId, String currency);
}
