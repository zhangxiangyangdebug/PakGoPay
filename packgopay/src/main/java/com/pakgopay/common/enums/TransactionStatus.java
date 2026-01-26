package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {
    PENDING(0, "PENDING"),
    PROCESSING(1, "PROCESSING"),
    SUCCESS(2, "SUCCESS"),
    FAILED(3, "FAILED"),
    EXPIRED(4, "EXPIRED"),
    CANCELLED(5, "CANCELLED");

    private final Integer code;
    private final String message;

    TransactionStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
