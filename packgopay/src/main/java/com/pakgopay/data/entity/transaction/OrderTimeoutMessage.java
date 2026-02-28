package com.pakgopay.data.entity.transaction;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transactionNo;
    private Long createTime;
    private Long sendTime;
}
