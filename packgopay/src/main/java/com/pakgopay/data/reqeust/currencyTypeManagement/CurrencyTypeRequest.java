package com.pakgopay.data.reqeust.currencyTypeManagement;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CurrencyTypeRequest extends BaseRequest {
    private Integer id;
    private String name;
    private String currencyType;
    private String icon;
    private BigDecimal currencyAccuracy;
    private Integer isRate;
    private BigDecimal exchangeRate;
    private Integer pageNo;
    private Integer pageSize;
    public Integer getOffSet() {
        return (pageNo - 1) * pageSize;
    }
}
