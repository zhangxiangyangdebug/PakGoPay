package com.pakgopay.util;

import com.pakgopay.common.constant.CommonConstant;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.*;
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

    /**
     * Calculate merchant/agent/platform profits by tier, tolerating missing tiers.
     */
    public static FeeProfitResult calculateTierProfits(FeeCalcInput input) {
        // Agent cases supported:
        // 1) no agent
        // 2) only level-1
        // 3) only level-2
        // 4) only level-3
        // 5) level-2 + level-3
        // 6) level-1 + level-2
        // 7) level-1 + level-2 + level-3
        // Profit rule: each level submits its fee upstream; profit = downstream fee - upstream fee.
        // Platform profit equals the top-most upstream fee; no agent -> merchant fee.
        if (input == null) {
            return new FeeProfitResult();
        }
        BigDecimal merchantFee = calculateFee(input.amount, input.merchantRate, input.merchantFixed);

        BigDecimal level1Fee = calculateFee(input.amount, input.agent1Rate, input.agent1Fixed);
        BigDecimal level2Fee = calculateFee(input.amount, input.agent2Rate, input.agent2Fixed);
        BigDecimal level3Fee = calculateFee(input.amount, input.agent3Rate, input.agent3Fixed);

        // Level-3 profit: merchant fee minus level-3 fee (if level-3 exists).
        BigDecimal profit3 = isPositive(level3Fee)
                ? maxZero(safeSubtract(merchantFee, level3Fee))
                : BigDecimal.ZERO;

        // Level-2 profit: fee from downstream (level-3 or merchant) minus level-2 fee.
        BigDecimal level2Downstream = firstPositiveOr(level3Fee, merchantFee);
        BigDecimal profit2 = isPositive(level2Fee)
                ? maxZero(safeSubtract(level2Downstream, level2Fee))
                : BigDecimal.ZERO;

        // Level-1 profit: fee from downstream (level-2/3/merchant) minus level-1 fee.
        BigDecimal level1Downstream = firstPositiveOr(level2Fee, level3Fee, merchantFee);
        BigDecimal profit1 = isPositive(level1Fee)
                ? maxZero(safeSubtract(level1Downstream, level1Fee))
                : BigDecimal.ZERO;

        // Platform profit: top-most available fee (level-1/2/3/merchant).
        BigDecimal platformProfit = firstPositiveOr(level1Fee, level2Fee, level3Fee, merchantFee);

        FeeProfitResult result = new FeeProfitResult();
        result.merchantFee = merchantFee;
        result.agent1Profit = profit1;
        result.agent2Profit = profit2;
        result.agent3Profit = profit3;
        result.platformProfit = platformProfit;
        return result;
    }

    public static BigDecimal calculateFee(BigDecimal amount, BigDecimal rate, BigDecimal fixed) {
        BigDecimal fee = BigDecimal.ZERO;
        if (fixed != null && fixed.compareTo(BigDecimal.ZERO) != 0) {
            fee = safeAdd(fee, fixed);
        }
        if (rate != null && rate.compareTo(BigDecimal.ZERO) != 0) {
            fee = safeAdd(fee, calculate(amount, rate, 6));
        }
        return fee;
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    /**
     * Return true if value is not null and greater than zero.
     */
    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Return the first positive value in order, or zero if none.
     */
    private static BigDecimal firstPositiveOr(BigDecimal... values) {
        BigDecimal fallback = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            fallback = value;
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return fallback;
    }

    public static class FeeProfitResult {
        public BigDecimal merchantFee = BigDecimal.ZERO;
        public BigDecimal agent1Profit = BigDecimal.ZERO;
        public BigDecimal agent2Profit = BigDecimal.ZERO;
        public BigDecimal agent3Profit = BigDecimal.ZERO;
        public BigDecimal platformProfit = BigDecimal.ZERO;
    }

    public static class FeeCalcInput {
        public BigDecimal amount;
        public BigDecimal merchantRate;
        public BigDecimal merchantFixed;
        public BigDecimal agent1Rate;
        public BigDecimal agent1Fixed;
        public BigDecimal agent2Rate;
        public BigDecimal agent2Fixed;
        public BigDecimal agent3Rate;
        public BigDecimal agent3Fixed;
    }

    public static String resolveSupportTypeLabel(Integer supportType) {
        if (supportType == null) {
            return "unknown";
        }
        if (Objects.equals(CommonConstant.SUPPORT_TYPE_COLLECTION, supportType)) {
            return "collection";
        }
        if (Objects.equals(CommonConstant.SUPPORT_TYPE_PAY, supportType)) {
            return "payout";
        }
        if (Objects.equals(CommonConstant.SUPPORT_TYPE_ALL, supportType)) {
            return "collection/payout";
        }
        return String.valueOf(supportType);
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

    /**
     * Parse IP whitelist string to set.
     *
     * @param ipWhitelist comma-separated IPs
     * @return allowed IP set
     */
    public static Set<String> parseIpWhitelist(String ipWhitelist) {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return Set.of("127.0.0.1");
        }
        return Arrays.stream(ipWhitelist.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Apply MDC context for balance logging during a runnable execution.
     *
     * @param source source label (e.g. interface name)
     * @param transactionNo system transaction no
     * @param runnable execution block
     */
    public static void withBalanceLogContext(String source, String transactionNo, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        String prevSource = MDC.get("balanceSource");
        String prevTransactionNo = MDC.get("balanceTransactionNo");
        boolean setSource = false;
        boolean setTransactionNo = false;
        if (prevSource == null && source != null && !source.isBlank()) {
            MDC.put("balanceSource", source);
            setSource = true;
        }
        if (prevTransactionNo == null && transactionNo != null && !transactionNo.isBlank()) {
            MDC.put("balanceTransactionNo", transactionNo);
            setTransactionNo = true;
        }
        try {
            runnable.run();
        } finally {
            if (setSource) {
                MDC.remove("balanceSource");
            }
            if (setTransactionNo) {
                MDC.remove("balanceTransactionNo");
            }
        }
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
