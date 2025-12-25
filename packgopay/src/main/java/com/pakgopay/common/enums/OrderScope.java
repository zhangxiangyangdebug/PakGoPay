package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderScope {
    SYSTEM(0, "SYSTEM"), THIRD_PARTY(0, "THIRD_PARTY");


    private final Integer code;
    private final String message;

    OrderScope(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
