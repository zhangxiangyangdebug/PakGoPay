package com.pakgopay.service.common;

import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;

import java.math.BigDecimal;

public interface AccountEventService {

    /**
     * Build collection-success accounting events:
     * merchant credit + (optional) agent fee credit.
     */
    void appendCollectionSuccessEvents(CollectionOrderDto collectionOrderDto, MerchantInfoDto merchantInfoDto);

    /**
     * Build payout-success accounting events:
     * merchant frozen->deduct + (optional) agent fee credit.
     */
    void appendPayoutSuccessEvents(PayOrderDto payOrderDto, MerchantInfoDto merchantInfoDto, BigDecimal frozenAmount);

    /**
     * Build payout-failed accounting event:
     * merchant frozen->available rollback.
     */
    void appendPayoutFailedEvents(PayOrderDto payOrderDto, BigDecimal frozenAmount);

    /**
     * Consume pending/failed-retry events in small batches.
     *
     * @param limitSize per-round claimed row limit
     * @param maxRounds max rounds in one scheduler tick
     * @return total claimed rows in this invocation
     */
    int consumePendingEvents(int limitSize, int maxRounds);
}
