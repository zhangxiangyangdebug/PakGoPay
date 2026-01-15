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

    Map<String, Map<String, BigDecimal>> getBalanceInfos(List<String> userId) throws PakGoPayException;
}
