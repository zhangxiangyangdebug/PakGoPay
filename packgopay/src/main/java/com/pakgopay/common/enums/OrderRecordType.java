package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderRecordType {
    SYSTEM(1, "SYSTEM"),
    MANUAL(2, "MANUAL"),
    TEST_WITH_EXTERNAL(3, "TEST_WITH_EXTERNAL"),
    TEST_WITHOUT_EXTERNAL(4, "TEST_WITHOUT_EXTERNAL");

    private final Integer code;
    private final String message;

    OrderRecordType(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static OrderRecordType fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("OrderRecordType code is null");
        }
        for (OrderRecordType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrderRecordType code: " + code);
    }
}
