package com.pakgopay.data.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountEventDetailResponse {

    private String userName;
    private Integer roleId;
    private String currency;
    private BigDecimal amount;
    private String eventType;
    private Short status;
    private Long createTime;
    private Long updateTime;
}
