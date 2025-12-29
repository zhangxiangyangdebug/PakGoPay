package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.CollectionOrderDetailDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface CollectionOrderMapper {

    // ------------------------------------- collection_order ---------------------------------------------------------
    Integer isExitMerchantOrderNo(@Param(value = "merchantOrderNo") String merchantOrderNo);


    // ------------------------------------- collection_order_detail --------------------------------------------------
    Optional<CollectionOrderDetailDto> findByOrderId(@Param("orderId") String orderId);

    int insert(CollectionOrderDetailDto dto);

    int updateByOrderId(CollectionOrderDetailDto dto);

    /**
     * callBack times add +1
     */
    int increaseCallbackTimes(@Param("orderId") String orderId,
                              @Param("lastCallbackTime") LocalDateTime lastCallbackTime);

    List<CollectionOrderDetailDto> getCollectionOrderDetailInfosByPaymentIds(
            @Param("paymentIds") List<Long> paymentIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
