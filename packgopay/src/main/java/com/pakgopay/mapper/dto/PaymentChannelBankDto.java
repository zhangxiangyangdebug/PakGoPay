package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PaymentChannelBankDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long paymentId;
    private String bankCode;
    private String currency;
    private Integer supportType;
    private Integer status;
    private Long createTime;
    private Long updateTime;
}

