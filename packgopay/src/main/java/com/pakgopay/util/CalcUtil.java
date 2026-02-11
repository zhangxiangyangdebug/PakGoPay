package com.pakgopay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CalcUtil {

    private CalcUtil() {}

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

    public static BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static FeeProfitResult calculateTierProfits(FeeCalcInput input) {
        if (input == null) {
            return new FeeProfitResult();
        }
        BigDecimal merchantFee = calculateFee(input.amount, input.merchantRate, input.merchantFixed);

        BigDecimal level1Fee = calculateFee(input.amount, input.agent1Rate, input.agent1Fixed);
        BigDecimal level2Fee = calculateFee(input.amount, input.agent2Rate, input.agent2Fixed);
        BigDecimal level3Fee = calculateFee(input.amount, input.agent3Rate, input.agent3Fixed);

        BigDecimal profit3 = isPositive(level3Fee)
                ? maxZero(safeSubtract(merchantFee, level3Fee))
                : BigDecimal.ZERO;

        BigDecimal level2Downstream = firstPositiveOr(level3Fee, merchantFee);
        BigDecimal profit2 = isPositive(level2Fee)
                ? maxZero(safeSubtract(level2Downstream, level2Fee))
                : BigDecimal.ZERO;

        BigDecimal level1Downstream = firstPositiveOr(level2Fee, level3Fee, merchantFee);
        BigDecimal profit1 = isPositive(level1Fee)
                ? maxZero(safeSubtract(level1Downstream, level1Fee))
                : BigDecimal.ZERO;

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

    public static BigDecimal calculate(BigDecimal amount, BigDecimal rate, int scale) {
        if (amount == null || rate == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return amount
                .multiply(rate)
                .divide(ONE_HUNDRED, scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal firstPositiveOr(BigDecimal... values) {
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

    public static BigDecimal resolveSuccessRate(long success, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
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
}
