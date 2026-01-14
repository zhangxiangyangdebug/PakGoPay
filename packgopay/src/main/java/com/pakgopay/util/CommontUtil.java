package com.pakgopay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public static BigDecimal sum(List<BigDecimal> list) {
        BigDecimal total = BigDecimal.ZERO;

        if (list == null || list.isEmpty()) {
            return BigDecimal.ZERO;
        }

        for (BigDecimal v : list) {
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
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

    public static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException e) {
                        return null; // ignore invalid id
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
