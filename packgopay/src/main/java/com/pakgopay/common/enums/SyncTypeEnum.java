package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum SyncTypeEnum {
    CURRENCY(1, "sync currency"),
    BANK_CODE(2, "sync bank code");

    private final Integer type;
    private final String description;

    SyncTypeEnum(Integer type, String description) {
        this.type = type;
        this.description = description;
    }

    public static SyncTypeEnum fromType(Integer type) {
        if (type == null) {
            return null;
        }
        for (SyncTypeEnum value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }
}
