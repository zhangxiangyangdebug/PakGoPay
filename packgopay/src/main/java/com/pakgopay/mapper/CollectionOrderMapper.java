package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.CollectionOrderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface CollectionOrderMapper {

    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);

    /** Query order by order ID */
    Optional<CollectionOrderDto> findByOrderId(@Param("orderId") String orderId);

    /** Insert order */
    int insert(CollectionOrderDto dto);

    /** Update order by order ID */
    int updateByOrderId(CollectionOrderDto dto);

    /** Increase callback retry times */
    int increaseCallbackTimes(@Param("orderId") String orderId,
                              @Param("lastCallbackTime") LocalDateTime lastCallbackTime);

    /** Query orders by payment IDs and time range */
    List<CollectionOrderDto> getCollectionOrderInfosByPaymentIds(
            @Param("paymentIds") List<Long> paymentIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
