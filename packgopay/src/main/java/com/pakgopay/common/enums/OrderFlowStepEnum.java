package com.pakgopay.common.enums;

import lombok.Getter;

@Getter
public enum OrderFlowStepEnum {
    MERCHANT_CREATE_REQUEST(1, "MERCHANT_CREATE_REQUEST", "Merchant Create Request"),
    CHANNEL_SELECTED(2, "CHANNEL_SELECTED", "Channel Selected"),
    AGENT_CHAIN_RESOLVED(3, "AGENT_CHAIN_RESOLVED", "Agent Chain Resolved"),
    THIRD_BALANCE_REQUEST(4, "THIRD_BALANCE_REQUEST", "Third Balance Request"),
    THIRD_BALANCE_RESPONSE(5, "THIRD_BALANCE_RESPONSE", "Third Balance Response"),
    THIRD_CREATE_REQUEST(6, "THIRD_CREATE_REQUEST", "Third Create Request"),
    THIRD_CREATE_RESPONSE(7, "THIRD_CREATE_RESPONSE", "Third Create Response"),
    MERCHANT_CREATE_RESPONSE(8, "MERCHANT_CREATE_RESPONSE", "Merchant Create Response"),
    THIRD_NOTIFY_REQUEST(9, "THIRD_NOTIFY_REQUEST", "Third Notify Request"),
    THIRD_QUERY_REQUEST(10, "THIRD_QUERY_REQUEST", "Third Query Request"),
    THIRD_QUERY_RESPONSE(11, "THIRD_QUERY_RESPONSE", "Third Query Response"),
    MERCHANT_NOTIFY_REQUEST(12, "MERCHANT_NOTIFY_REQUEST", "Merchant Notify Request"),
    MERCHANT_NOTIFY_RESPONSE(13, "MERCHANT_NOTIFY_RESPONSE", "Merchant Notify Response"),
    NOTIFY_API_RESPONSE(14, "NOTIFY_API_RESPONSE", "Notify API Response");

    private final Integer seq;
    private final String code;
    private final String name;

    OrderFlowStepEnum(Integer seq, String code, String name) {
        this.seq = seq;
        this.code = code;
        this.name = name;
    }
}
