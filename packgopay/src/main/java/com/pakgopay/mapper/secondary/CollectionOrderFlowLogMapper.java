package com.pakgopay.mapper.secondary;

import com.pakgopay.mapper.dto.OrderFlowLogDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CollectionOrderFlowLogMapper {

    int insert(OrderFlowLogDto dto);

    int insertBatch(@Param("tableName") String tableName, @Param("list") List<OrderFlowLogDto> list);

    List<OrderFlowLogDto> listByTransactionNo(
            @Param("transactionNo") String transactionNo,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);
}
