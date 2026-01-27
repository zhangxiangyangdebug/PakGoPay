package com.pakgopay.data.entity;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderQueryEntity {

    private String merchantUserId;
    private String transactionNo;
    private String merchantOrderNo;
    private String currencyType;
    private String orderStatus;
    private Integer orderType;
    private BigDecimal amount;
    private Long channelId;
    private Long startTime;
    private Long endTime;
    private Integer pageNo;
    private Integer pageSize;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
