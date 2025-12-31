package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    // Pending payment: The order has been created but payment has not been received.
    PENDING(0, "PENDING"),
    // Processing, Payment request received and being processed.
    PROCESSING(1,"PROCESSING"),
    // Payment successful, order completed.
    SUCCESS(2,"SUCCESS"),
    // Payment failure may be due to insufficient balance or other reasons.
    FAILED(3,"FAILED"),
    // The order has expired; payment has not been completed by the due date.
    EXPIRED(4,"EXPIRED"),
    // The order has been cancelled, either by the user or by the administrator.
    CANCELLED(5,"CANCELLED");

    private final Integer code;
    private final String message;

    OrderStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
