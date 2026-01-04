package com.pakgopay.common.reqeust.transaction;

import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = "merchant_id is empty")
    private String merchantId;

    /**
     * Merchant order number (must be unique)
     */
    @NotBlank(message = "merchantOrderNo is empty")
    private String merchantOrderNo;

    /**
     * Merchant channel code (must be unique)
     */
    @NotNull(message = "paymentNo is empty")
    private Integer paymentNo;

    /**
     * Collection amount
     */
    @NotNull(message = "amount is empty")
    private BigDecimal amount;

    /**
     * Currency code (e.g. VND, PKR, IDR, USD, CNY)
     */
    @NotBlank(message = "currency is empty")
    private String currency;

    /**
     * Asynchronous notification URL
     */
    @NotBlank(message = "notificationUrl is empty")
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
