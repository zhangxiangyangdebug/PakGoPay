package com.pakgopay.service.balance;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.response.CommonResponse;

public interface BalanceService {

    CommonResponse queryMerchantAvailableBalance(String userId) throws PakGoPayException;
}
