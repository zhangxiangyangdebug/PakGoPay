package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CurrencyTypeDTO {
    private Integer id;
    private String currencyType;
    private BigDecimal currencyAccuracy;
    private String name;
    private String icon;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private BigDecimal exchangeRate;
    private String remark;
    private Integer isRate;
}
