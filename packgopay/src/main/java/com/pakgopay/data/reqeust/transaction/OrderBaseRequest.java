package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.BaseRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @NotBlank(message = "paymentNo is empty")
    private String paymentNo;

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
     * Request signature
     */
    private String sign;

    /**
     * Channel-specific parameters
     */
    private Object channelParams;

    /**
     * Remark
     */
    private String remark;

    /**
     * Manual order type for manual-create APIs only:
     * 2 = manual order, 3 = test with external call, 4 = test without external call.
     */
    @Min(value = 2, message = "manualOrderType must be 2, 3 or 4")
    @Max(value = 4, message = "manualOrderType must be 2, 3 or 4")
    private Integer manualOrderType;
}
