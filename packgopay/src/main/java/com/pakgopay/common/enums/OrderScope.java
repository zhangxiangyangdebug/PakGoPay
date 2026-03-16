package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderScope {
    THIRD_PARTY(1, "THIRD_PARTY"),
    SYSTEM(2, "SYSTEM");


    private final Integer code;
    private final String message;

    OrderScope(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static OrderScope fromIsThird(String isThird) {
        return "1".equals(isThird) ? THIRD_PARTY : SYSTEM;
    }
}
