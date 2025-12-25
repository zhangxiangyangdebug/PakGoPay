package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderType {
    COLLECTION_ORDER(0, "COLLECTION_ORDER"),
    PAY_OUT_ORDER(0, "PAY_OUT_ORDER");

    private final Integer code;
    private final String message;

    OrderType(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
