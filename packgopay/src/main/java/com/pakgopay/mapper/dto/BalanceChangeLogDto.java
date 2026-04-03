package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BalanceChangeLogDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String merchantUserId;
    private String currency;
    private String bizType;
    private String bizNo;
    private BigDecimal changeAmount;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private Long createdAt;
}
