package com.pakgopay.common.reqeust.currencyTypeManagement;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CurrencyTypeRequest extends BaseRequest {
    private String name;
    private String currencyType;
    private String icon;
    private BigDecimal currencyAccuracy;
    private Integer isRate;
    private BigDecimal exchangeRate;
}
