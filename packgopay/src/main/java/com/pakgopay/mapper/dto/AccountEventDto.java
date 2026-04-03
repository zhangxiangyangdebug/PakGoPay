package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AccountEventDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String eventType;
    private String bizNo;
    private String userId;
    private String currency;
    private BigDecimal amount;
    private Long eventTime;
    private Short status;
    private Integer retryCount;
    private String batchNo;
    private String lastError;
    private Long createdAt;
    private Long updatedAt;
}
