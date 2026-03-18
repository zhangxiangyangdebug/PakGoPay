package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BankCodeDictDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String bankName;
    private String bankCode;
    private String country;
    private String currencyName;
    private String currencyCode;
    private Long createTime;
    private Long updateTime;
}

