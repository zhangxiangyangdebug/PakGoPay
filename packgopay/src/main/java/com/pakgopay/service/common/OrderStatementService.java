package com.pakgopay.service.common;

import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;

import java.math.BigDecimal;

public interface OrderStatementService {

    /**
     * Write merchant/agent statement rows for one collection success notification.
     */
    void recordCollectionSuccessStatements(CollectionOrderDto collectionOrderDto, MerchantInfoDto merchantInfoDto);

    /**
     * Write merchant/agent statement rows for one payout success notification.
     */
    void recordPayoutSuccessStatements(PayOrderDto payOrderDto, MerchantInfoDto merchantInfoDto, BigDecimal frozenAmount);
}
