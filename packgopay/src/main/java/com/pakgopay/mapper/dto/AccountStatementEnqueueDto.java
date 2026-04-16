package com.pakgopay.mapper.dto;

import lombok.Data;

@Data
public class AccountStatementEnqueueDto {
    private String userId;
    private String currency;
    private Long createTime;
}
