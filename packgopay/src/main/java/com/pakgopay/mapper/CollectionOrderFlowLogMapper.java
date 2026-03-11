package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.OrderFlowLogDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionOrderFlowLogMapper {

    int insert(OrderFlowLogDto dto);

    int insertBatch(List<OrderFlowLogDto> list);

    List<OrderFlowLogDto> listByTransactionNo(
            @Param("transactionNo") String transactionNo,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);
}
