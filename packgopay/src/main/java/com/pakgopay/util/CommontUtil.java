package com.pakgopay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CommontUtil {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

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

    public static BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null) {
            a = BigDecimal.ZERO;
        }
        if (b == null) {
            b = BigDecimal.ZERO;
        }
        return a.subtract(b);
    }

    public static BigDecimal calculate(
            BigDecimal amount,
            BigDecimal rate,
            int scale) {

        if (amount == null || rate == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }

        return amount
                .multiply(rate)
                .divide(ONE_HUNDRED, scale, RoundingMode.HALF_UP);
    }
}
