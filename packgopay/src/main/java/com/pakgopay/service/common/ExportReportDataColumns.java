package com.pakgopay.service.common;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExportReportDataColumns {
    private ExportReportDataColumns() {
    }

    // merchant report export file name
    public static final String MERCHANT_REPORT_EXPORT_FILE_NAME = "merchant_report.xlsx";

    // channel report export file name
    public static final String CHANNEL_REPORT_EXPORT_FILE_NAME = "channel_report.xlsx";

    // agent report export file name
    public static final String AGENT_REPORT_EXPORT_FILE_NAME = "agent_report.xlsx";

    // currency report export file name
    public static final String CURRENCY_REPORT_EXPORT_FILE_NAME = "currency_report.xlsx";

    // payment report export file name
    public static final String PAYMENT_REPORT_EXPORT_FILE_NAME = "payment_report.xlsx";

    // channel export file name
    public static final String CHANNEL_EXPORT_FILE_NAME = "channel.xlsx";

    // payment export file name
    public static final String PAYMENT_EXPORT_FILE_NAME = "payment.xlsx";

    // Exporting 10,000 rows at a time
    public static final Integer EXPORT_PAGE_SIZE = 10000;

    // Each sheet can have a maximum of 50,000 rows.
    public static final Integer EXPORT_SHEET_ROW_LIMIT = 50000;

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<MerchantReportDto>> MERCHANT_REPORT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<ChannelReportDto>> CHANNEL_REPORT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<AgentReportDto>> AGENT_REPORT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<CurrencyReportDto>> CURRENCY_REPORT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<PaymentReportDto>> PAYMENT_REPORT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<ChannelDto>> CHANNEL_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<PaymentDto>> PAYMENT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<AgentInfoDto>> AGENT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<WithdrawalAccountsDto>> AGENT_ACCOUNT_ALLOWED = new LinkedHashMap<>();

    // key -> (title default value, value function)
    public static final Map<String, ColumnDef<WithdrawalAccountsDto>> MERCHANT_ACCOUNT_ALLOWED = new LinkedHashMap<>();

    static {
        // merchant report, export column
        MERCHANT_REPORT_ALLOWED.put("merchantName", new ColumnDef<>("merchantName", MerchantReportDto::getMerchantName));

        MERCHANT_REPORT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        MERCHANT_REPORT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        MERCHANT_REPORT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        MERCHANT_REPORT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));

        MERCHANT_REPORT_ALLOWED.put("agent1Fee", new ColumnDef<>("agent1Fee", r -> safeToString(r.getAgent1Fee())));
        MERCHANT_REPORT_ALLOWED.put("agent2Fee", new ColumnDef<>("agent2Fee", r -> safeToString(r.getAgent2Fee())));
        MERCHANT_REPORT_ALLOWED.put("agent3Fee", new ColumnDef<>("agent3Fee", r -> safeToString(r.getAgent3Fee())));

        MERCHANT_REPORT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));

        MERCHANT_REPORT_ALLOWED.put("currency", new ColumnDef<>("currency", MerchantReportDto::getCurrency));
        MERCHANT_REPORT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // channel report, export column
        CHANNEL_REPORT_ALLOWED.put("channelName", new ColumnDef<>("channelName", ChannelReportDto::getChannelName));

        CHANNEL_REPORT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        CHANNEL_REPORT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        CHANNEL_REPORT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        CHANNEL_REPORT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        CHANNEL_REPORT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        CHANNEL_REPORT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));

        CHANNEL_REPORT_ALLOWED.put("currency", new ColumnDef<>("currency", ChannelReportDto::getCurrency));
        CHANNEL_REPORT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // agent report, export column
        AGENT_REPORT_ALLOWED.put("agentName", new ColumnDef<>("agentName", AgentReportDto::getAgentName));

        AGENT_REPORT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        AGENT_REPORT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        AGENT_REPORT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        AGENT_REPORT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        AGENT_REPORT_ALLOWED.put("commission", new ColumnDef<>("commission", r -> safeToString(r.getCommission())));

        AGENT_REPORT_ALLOWED.put("currency", new ColumnDef<>("currency", AgentReportDto::getCurrency));
        AGENT_REPORT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // currency report, export column
        CURRENCY_REPORT_ALLOWED.put("currency", new ColumnDef<>("currency", CurrencyReportDto::getCurrency));

        CURRENCY_REPORT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        CURRENCY_REPORT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        CURRENCY_REPORT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        CURRENCY_REPORT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        CURRENCY_REPORT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        CURRENCY_REPORT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));
        CURRENCY_REPORT_ALLOWED.put("orderBalance", new ColumnDef<>("orderBalance", r -> safeToString(r.getOrderBalance())));

        CURRENCY_REPORT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // payment report, export column
        PAYMENT_REPORT_ALLOWED.put("paymentName", new ColumnDef<>("paymentName", PaymentReportDto::getPaymentName));
        PAYMENT_REPORT_ALLOWED.put("paymentNo", new ColumnDef<>("paymentNo", r -> String.valueOf(r.getPaymentId())));

        PAYMENT_REPORT_ALLOWED.put("orderQuantity", new ColumnDef<>("orderQuantity", r -> safeToString(r.getOrderQuantity())));
        PAYMENT_REPORT_ALLOWED.put("orderSuccessRate", new ColumnDef<>("orderSuccessRate", r -> safeCalculateRate(r.getOrderQuantity(), r.getSuccessQuantity())));
        PAYMENT_REPORT_ALLOWED.put("failedQuantity", new ColumnDef<>("failedQuantity", r -> safeToString(r.getOrderQuantity() - r.getSuccessQuantity())));
        PAYMENT_REPORT_ALLOWED.put("successQuantity", new ColumnDef<>("successQuantity", r -> safeToString(r.getSuccessQuantity())));

        PAYMENT_REPORT_ALLOWED.put("merchantFee", new ColumnDef<>("merchantFee", r -> safeToString(r.getMerchantFee())));
        PAYMENT_REPORT_ALLOWED.put("orderProfit", new ColumnDef<>("orderProfit", r -> safeToString(r.getOrderProfit())));
        PAYMENT_REPORT_ALLOWED.put("orderBalance", new ColumnDef<>("orderBalance", r -> safeToString(r.getOrderBalance())));

        PAYMENT_REPORT_ALLOWED.put("currency", new ColumnDef<>("currency", PaymentReportDto::getCurrency));
        PAYMENT_REPORT_ALLOWED.put("timeDate", new ColumnDef<>("timeDate", r -> safeToString(r.getRecordDate())));

        //--------------------------------------------------------------------------------------------------------------

        // channel, export column
        CHANNEL_ALLOWED.put("channelName", new ColumnDef<>("channelName", ChannelDto::getChannelName));

        CHANNEL_ALLOWED.put("collectPayment",
                new ColumnDef<>("collectPayment", r -> safeToPaymentInfo(r.getPaymentDtoList(), new ArrayList<>() {{
            add(CommonConstant.SUPPORT_TYPE_COLLECTION);
            add(CommonConstant.SUPPORT_TYPE_ALL);
        }})));


    }


    private static String safeToPaymentInfo(List<PaymentDto> paymentDtoList, List<Integer> supportTypes){
        if (paymentDtoList == null || paymentDtoList.isEmpty()) return "";
        return paymentDtoList.stream()
                .filter(p-> supportTypes.contains(p.getSupportType()))
                .map(p -> "paymentNo:" + p.getPaymentNo() + ", paymentName:" + p.getPaymentName())
                .collect(Collectors.joining(" | "));
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
