package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AccountEventQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String userName;
    private Integer roleId;
    private String currency;
    private BigDecimal amount;
    private String eventType;
    private Short status;
    private Long createTime;
    private Long updateTime;
}
