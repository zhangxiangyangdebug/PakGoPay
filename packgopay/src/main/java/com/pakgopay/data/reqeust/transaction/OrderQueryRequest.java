package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderQueryRequest extends ExportBaseRequest {

    /** merchant user id */
    private String merchantUserId;

    /** system transaction no */
    private String transactionNo;

    /** merchant order no */
    private String merchantOrderNo;

    /** currency code */
    private String currency;

    /** order status */
    private String orderStatus;

    /** order type: 1-system, 2-manual */
    private Integer orderType;

    /** order amount */
    private BigDecimal amount;

    /** channel id */
    private Long channelId;

    /** create_time >= startTime (unix seconds) */
    @NotNull(message = "startTime is null")
    private Long startTime;

    /** create_time < endTime (unix seconds) */
    @NotNull(message = "endTime is null")
    private Long endTime;
}
