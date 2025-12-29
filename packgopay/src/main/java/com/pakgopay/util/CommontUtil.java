package com.pakgopay.util;

import java.math.BigDecimal;

public class CommontUtil {

    public static BigDecimal safeAdd(BigDecimal... values) {
        BigDecimal result = BigDecimal.ZERO;
        if (values == null) {
            return result;
        }
        for (BigDecimal v : values) {
            if (v != null) {
                result = result.add(v);
            }
        }
        return result;
    }

}
