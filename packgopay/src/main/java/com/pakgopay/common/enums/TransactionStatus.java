package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {
    PENDING(0, "PENDING"),
    PROCESSING(1, "PROCESSING"),
    SUCCESS(2, "SUCCESS"),
    FAILED(3, "FAILED"),
    EXPIRED(4, "EXPIRED"),
    REVERSED(5, "REVERSED");

    private final Integer code;
    private final String message;

    TransactionStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static TransactionStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("transaction status code is empty");
        }
        for (TransactionStatus status : values()) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unsupported transaction status code: " + code);
    }
}
