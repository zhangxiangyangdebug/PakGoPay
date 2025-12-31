package com.pakgopay.common.reqeust.currencyTypeManagement;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

@Data
public class CurrencyTypeRequest extends BaseRequest {
    private String currencyName;
    private String currencyIcon;
    private Float currencyAccuracy;
    private Integer currencyModel;
    private Float currencyRate;
}
