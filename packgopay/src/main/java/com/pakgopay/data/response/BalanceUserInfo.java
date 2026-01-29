package com.pakgopay.data.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class BalanceUserInfo {

    /**
     * total summary by currency
     */
    private Map<String, Map<String, BigDecimal>> totalData;

    /**
     * per-user summary: userId -> currency -> balances
     */
    private Map<String, Map<String, Map<String, BigDecimal>>> userDataMap;
}
