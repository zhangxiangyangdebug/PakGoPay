package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceBucketDeltaDto {

    private Integer bucketNo;

    private BigDecimal availableDelta;

    private BigDecimal frozenDelta;

    private BigDecimal totalDelta;

    private BigDecimal withdrawDelta;
}
