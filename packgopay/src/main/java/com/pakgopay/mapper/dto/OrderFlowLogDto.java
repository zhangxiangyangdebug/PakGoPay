package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class OrderFlowLogDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String transactionNo;
    private String stepCode;
    private String stepName;
    private Integer stepSeq;
    private Boolean success;
    private String payload;
    private String traceId;
    private Long eventTime;
    private Long createTime;
}
