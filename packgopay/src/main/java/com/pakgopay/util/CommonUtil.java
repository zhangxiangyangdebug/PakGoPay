package com.pakgopay.util;

import com.pakgopay.common.constant.CommonConstant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.time.ZoneId;
import java.util.stream.Collectors;

public class CommonUtil {

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

    /**
     * Safe empty list fallback.
     *
     * @param list input list
     * @return safe list
     */
    public static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Default integer fallback.
     *
     * @param value input value
     * @param defaultValue fallback value
     * @return resolved integer
     */
    public static Integer defaultInt(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Default BigDecimal fallback.
     *
     * @param value input value
     * @return resolved BigDecimal
     */
    public static BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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

    public static boolean supportsCollection(Integer supportType) {
        return CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)
                || CommonConstant.SUPPORT_TYPE_ALL.equals(supportType);
    }

    public static boolean supportsPay(Integer supportType) {
        return CommonConstant.SUPPORT_TYPE_PAY.equals(supportType)
                || CommonConstant.SUPPORT_TYPE_ALL.equals(supportType);
    }

    public static ZoneId resolveZoneIdByCurrency(String currency) {
        if (currency == null) {
            return ZoneId.systemDefault();
        }
        switch (currency.toUpperCase()) {
            case "US":
            case "USD":
                return ZoneId.of("America/New_York");
            default:
                // TODO add currency -> timezone mapping.
                return ZoneId.systemDefault();
        }
    }
}
