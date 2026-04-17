package com.pakgopay.mapper.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AccountStatementTaskCursorDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userId;
    private String currency;
    private String taskType;
    private String pendingMonth;
    private Long lastDoneSeq;
    private Long updatedTime;
}
