package com.pakgopay.service.report;

import com.pakgopay.mapper.dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ExportReportDataColumns {
    private ExportReportDataColumns() {
    }

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<MerchantReportDto>> MERCHANT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<ChannelReportDto>> CHANNEL_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<AgentReportDto>> AGENT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<CurrencyReportDto>> CURRENCY_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<PaymentReportDto>> PAYMENT_ALLOWED = new LinkedHashMap<>();

    static {
        // merchant report, export column
        MERCHANT_ALLOWED.put("merchantName", new ColumnDef<>("merchantName", MerchantReportDto::getMerchantName));

        MERCHANT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        MERCHANT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        MERCHANT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        MERCHANT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));

        MERCHANT_ALLOWED.put("agent1Fee", new ColumnDef<>("agent1Fee", r -> safeToString(r.getAgent1Fee())));
        MERCHANT_ALLOWED.put("agent2Fee", new ColumnDef<>("agent2Fee", r -> safeToString(r.getAgent2Fee())));
        MERCHANT_ALLOWED.put("agent3Fee", new ColumnDef<>("agent3Fee", r -> safeToString(r.getAgent3Fee())));

        MERCHANT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));

        MERCHANT_ALLOWED.put("currency", new ColumnDef<>("currency", MerchantReportDto::getCurrency));
        MERCHANT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // merchant report, export column
        CHANNEL_ALLOWED.put("channelName", new ColumnDef<>("channelName", ChannelReportDto::getChannelName));

        CHANNEL_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        CHANNEL_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        CHANNEL_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        CHANNEL_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        CHANNEL_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        CHANNEL_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));

        CHANNEL_ALLOWED.put("currency", new ColumnDef<>("currency", ChannelReportDto::getCurrency));
        CHANNEL_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // merchant report, export column
        AGENT_ALLOWED.put("agentName", new ColumnDef<>("agentName", AgentReportDto::getAgentName));

        AGENT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        AGENT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        AGENT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        AGENT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        AGENT_ALLOWED.put("commission", new ColumnDef<>("commission", r -> safeToString(r.getCommission())));

        AGENT_ALLOWED.put("currency", new ColumnDef<>("currency", AgentReportDto::getCurrency));
        AGENT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // merchant report, export column
        CURRENCY_ALLOWED.put("currency", new ColumnDef<>("currency", CurrencyReportDto::getCurrency));

        CURRENCY_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        CURRENCY_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        CURRENCY_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        CURRENCY_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        CURRENCY_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        CURRENCY_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));
        CURRENCY_ALLOWED.put("orderBalance", new ColumnDef<>("orderBalance", r -> safeToString(r.getOrderBalance())));

        CURRENCY_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // merchant report, export column
        PAYMENT_ALLOWED.put("paymentName", new ColumnDef<>("paymentName", PaymentReportDto::getPaymentName));
        PAYMENT_ALLOWED.put("paymentNo", new ColumnDef<>("paymentNo", r -> String.valueOf(r.getPaymentId())));

        PAYMENT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        PAYMENT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        PAYMENT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        PAYMENT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        PAYMENT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        PAYMENT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));
        PAYMENT_ALLOWED.put("orderBalance", new ColumnDef<>("orderBalance", r -> safeToString(r.getOrderBalance())));

        PAYMENT_ALLOWED.put("currency", new ColumnDef<>("currency", PaymentReportDto::getCurrency));
        PAYMENT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));
    }

    private static String safeToString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String safeCalculateRate(Integer total, Integer successNumber) {
        if (total == null || total <= 0) {
            return "0%";
        }
        if (successNumber == null || successNumber <= 0) {
            return "0%";
        }

        BigDecimal success = BigDecimal.valueOf(successNumber);
        BigDecimal t = BigDecimal.valueOf(total);

        // success / total * 100
        BigDecimal rate = success
                .multiply(BigDecimal.valueOf(100))
                .divide(t, 2, RoundingMode.HALF_UP);

        return rate.stripTrailingZeros().toPlainString() + "%";
    }


    public record ColumnDef<T>(String defaultTitle, Function<T, String> getter) {
    }
}
