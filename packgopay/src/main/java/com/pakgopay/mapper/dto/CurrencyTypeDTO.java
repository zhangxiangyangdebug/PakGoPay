package com.pakgopay.mapper.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CurrencyTypeDTO {
    private Integer id;
    private String currencyType;
    private BigDecimal currencyAccuracy;
    private String name;
    private String icon;
    private Long createTime;
    private Long updateTime;
    private String createBy;
    private String updateBy;
    private String remark;
}
