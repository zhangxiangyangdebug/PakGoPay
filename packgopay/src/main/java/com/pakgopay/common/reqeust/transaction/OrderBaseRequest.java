package com.pakgopay.common.reqeust.transaction;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderBaseRequest extends BaseRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Merchant Id
     */
    private String merchant_id;

    /**
     * Merchant order number (must be unique)
     */
    private String merchantOrderNo;

    /**
     * Merchant channel code (must be unique)
     */
    private String channelCode;

    /**
     * Collection amount
     */
    private BigDecimal amount;

    /**
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    private String currency;

    /**
     * Asynchronous notification URL
     */
    private String notificationUrl;

    /**
     * Channel-specific parameters
     */
    private Object channelParams;

    /**
     * Remark
     */
    private String remark;
}
