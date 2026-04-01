package com.pakgopay.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum AccountEventType {
    COLLECTION_CREDIT("COLLECTION_CREDIT"),
    AGENT_CREDIT("AGENT_CREDIT"),
    PAYOUT_CONFIRM("PAYOUT_CONFIRM"),
    PAYOUT_RELEASE("PAYOUT_RELEASE");

    private final String code;

    AccountEventType(String code) {
        this.code = code;
    }

    public static List<String> allCodes() {
        return Arrays.stream(values()).map(AccountEventType::getCode).collect(Collectors.toList());
    }
}
