package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.PayOrderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PayOrderMapper {

    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);

    /** Query order by order ID */
    Optional<PayOrderDto> findByOrderId(@Param("orderId") String orderId);

    /** Insert order */
    int insert(PayOrderDto dto);

    /** Update order by order ID (update non-null fields) */
    int updateByOrderId(PayOrderDto dto);

    /** Increase callback retry times (atomic) */
    int increaseCallbackTimes(@Param("orderId") String orderId,
                              @Param("lastCallbackTime") LocalDateTime lastCallbackTime);

    /** Query by payment IDs and time range */
    List<PayOrderDto> getPayOrderInfosByPaymentIds(@Param("paymentIds") List<Long> paymentIds,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
}

