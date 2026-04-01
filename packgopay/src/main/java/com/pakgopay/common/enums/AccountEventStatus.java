package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum AccountEventStatus {
    PENDING((short) 0),
    PROCESSING((short) 1),
    DONE((short) 2),
    FAILED((short) 3);

    private final short code;

    AccountEventStatus(short code) {
        this.code = code;
    }
}
