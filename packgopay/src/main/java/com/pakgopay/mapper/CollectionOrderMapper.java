package com.pakgopay.mapper;

import com.pakgopay.data.entity.OrderQueryEntity;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CollectionOrderMapper {

    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);

    /** Query order by order ID */
    Optional<CollectionOrderDto> findByTransactionNo(@Param("transactionNo") String transactionNo);

    /** Insert order */
    int insert(CollectionOrderDto dto);

    /** Update order by order ID */
    int updateByTransactionNo(CollectionOrderDto dto);

    /** Increase callback retry times */
    int increaseCallbackTimes(@Param("transactionNo") String transactionNo,
                              @Param("lastCallbackTime") Long lastCallbackTime);

    /** Query orders by payment IDs and time range */
    List<CollectionOrderDto> getCollectionOrderInfosByPaymentIds(
            @Param("paymentIds") List<Long> paymentIds,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime
    );

    /** Count by query */
    Integer countByQuery(@Param("q") OrderQueryEntity query);

    /** Page query */
    List<CollectionOrderDto> pageByQuery(@Param("q") OrderQueryEntity query);
}
