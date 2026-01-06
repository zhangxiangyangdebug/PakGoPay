package com.pakgopay.service.balance;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.response.CommonResponse;

import java.math.BigDecimal;

public interface BalanceService {

    CommonResponse queryMerchantAvailableBalance(String userId) throws PakGoPayException;

    void freezeBalance(
            BigDecimal freezeFee, String userId, String currency) throws PakGoPayException;
}
