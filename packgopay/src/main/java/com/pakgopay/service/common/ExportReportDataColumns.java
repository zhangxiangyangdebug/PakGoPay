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
        CHANNEL_ALLOWED.put("payPayment",
                new ColumnDef<>("collectPayment", r -> safeToPaymentInfo(r.getPaymentDtoList(), new ArrayList<>() {{
                    add(CommonConstant.SUPPORT_TYPE_COLLECTION);
                    add(CommonConstant.SUPPORT_TYPE_ALL);
                }})));

        CHANNEL_ALLOWED.put("status", new ColumnDef<>("status", r -> safeToEnable(r.getStatus())));
        CHANNEL_ALLOWED.put("currency", new ColumnDef<>("currency", r -> safeToCurrency(r.getPaymentDtoList())));

        CHANNEL_ALLOWED.put("createTime", new ColumnDef<>("createTime", r -> safeToString(r.getCreateTime())));

        //--------------------------------------------------------------------------------------------------------------

        // payment, export column
        PAYMENT_ALLOWED.put("paymentNo", new ColumnDef<>("paymentNo", PaymentDto::getPaymentNo));
        PAYMENT_ALLOWED.put("paymentName", new ColumnDef<>("paymentName", PaymentDto::getPaymentName));
        PAYMENT_ALLOWED.put("status", new ColumnDef<>("status", r -> safeToEnable(r.getStatus())));
        PAYMENT_ALLOWED.put("isThird", new ColumnDef<>("isThird", PaymentDto::getIsThird));
        PAYMENT_ALLOWED.put("supportType", new ColumnDef<>("supportType", r -> safeToSupportType(r.getSupportType())));
        PAYMENT_ALLOWED.put("enableTimePeriod", new ColumnDef<>("enableTimePeriod", PaymentDto::getEnableTimePeriod));
        PAYMENT_ALLOWED.put("paymentType", new ColumnDef<>("paymentType", r -> safeToPaymentType(r.getPaymentType())));
        PAYMENT_ALLOWED.put("isCheckoutCounter", new ColumnDef<>("isCheckoutCounter", r -> safeToEnable(r.getIsCheckoutCounter())));
        PAYMENT_ALLOWED.put("collectionDailyLimit", new ColumnDef<>("collectionDailyLimit", r -> safeToString(r.getCollectionDailyLimit())));
        PAYMENT_ALLOWED.put("collectionMonthlyLimit", new ColumnDef<>("collectionMonthlyLimit", r -> safeToString(r.getCollectionMonthlyLimit())));
        PAYMENT_ALLOWED.put("payDailyLimit", new ColumnDef<>("payDailyLimit", r -> safeToString(r.getPayDailyLimit())));
        PAYMENT_ALLOWED.put("payMonthlyLimit", new ColumnDef<>("payMonthlyLimit", r -> safeToString(r.getPayMonthlyLimit())));
        PAYMENT_ALLOWED.put("paymentRequestPayUrl", new ColumnDef<>("paymentRequestPayUrl", r -> safeToString(r.getPaymentRequestPayUrl())));
        PAYMENT_ALLOWED.put("paymentRequestCollectionUrl", new ColumnDef<>("paymentRequestCollectionUrl", r -> safeToString(r.getPaymentRequestCollectionUrl())));
        PAYMENT_ALLOWED.put("paymentCollectionRate", new ColumnDef<>("paymentCollectionRate", r -> safeToString(r.getPaymentCollectionRate())));
        PAYMENT_ALLOWED.put("paymentPayRate", new ColumnDef<>("paymentPayRate", r -> safeToString(r.getPaymentPayRate())));
        PAYMENT_ALLOWED.put("paymentCheckPayUrl", new ColumnDef<>("paymentCheckPayUrl", r -> safeToString(r.getPaymentCheckPayUrl())));
        PAYMENT_ALLOWED.put("paymentCheckCollectionUrl", new ColumnDef<>("paymentCheckCollectionUrl", r -> safeToString(r.getPaymentCheckCollectionUrl())));
        PAYMENT_ALLOWED.put("collectionCallbackAddr", new ColumnDef<>("collectionCallbackAddr", r -> safeToString(r.getCollectionCallbackAddr())));
        PAYMENT_ALLOWED.put("payCallbackAddr", new ColumnDef<>("payCallbackAddr", r -> safeToString(r.getPayCallbackAddr())));
        PAYMENT_ALLOWED.put("checkoutCounterUrl", new ColumnDef<>("checkoutCounterUrl", r -> safeToString(r.getCheckoutCounterUrl())));
        PAYMENT_ALLOWED.put("currency", new ColumnDef<>("currency", r -> safeToString(r.getCurrency())));
        PAYMENT_ALLOWED.put("paymentMaxAmount", new ColumnDef<>("paymentMaxAmount", r -> safeToString(r.getPaymentMaxAmount())));
        PAYMENT_ALLOWED.put("paymentMinAmount", new ColumnDef<>("paymentMinAmount", r -> safeToString(r.getPaymentMinAmount())));
        PAYMENT_ALLOWED.put("bankName", new ColumnDef<>("bankName", r -> safeToString(r.getBankName())));
        PAYMENT_ALLOWED.put("bankAccount", new ColumnDef<>("bankAccount", r -> safeToString(r.getBankAccount())));
        PAYMENT_ALLOWED.put("bankUserName", new ColumnDef<>("bankUserName", r -> safeToString(r.getBankUserName())));

        // agent, export column
        AGENT_ALLOWED.put("agentName", new ColumnDef<>("agentName", AgentInfoDto::getAgentName));
        AGENT_ALLOWED.put("agentAccountName", new ColumnDef<>("agentAccountName", AgentInfoDto::getAccountName));
        AGENT_ALLOWED.put("channelInfos", new ColumnDef<>("channelInfos", r -> safeToChannelInfo(r.getChannelDtoList())));
        AGENT_ALLOWED.put("parentAgentName", new ColumnDef<>("parentAgentName", AgentInfoDto::getParentAgentName));
        AGENT_ALLOWED.put("parentAccountName", new ColumnDef<>("parentAccountName", AgentInfoDto::getParentUserName));
        AGENT_ALLOWED.put("parentChannelInfos", new ColumnDef<>("parentChannelInfos", r -> safeToChannelInfo(r.getParentChannelDtoList())));
        AGENT_ALLOWED.put("level", new ColumnDef<>("level", r -> safeToString(r.getLevel())));
        AGENT_ALLOWED.put("status", new ColumnDef<>("status", r -> safeToEnable(r.getStatus())));
        AGENT_ALLOWED.put("payRate", new ColumnDef<>("payRate", r -> safeToString(r.getPayRate())));
        AGENT_ALLOWED.put("payFixedFee", new ColumnDef<>("payFixedFee", r -> safeToString(r.getPayFixedFee())));
        AGENT_ALLOWED.put("payMaxFee", new ColumnDef<>("payMaxFee", r -> safeToString(r.getPayMaxFee())));
        AGENT_ALLOWED.put("payMinFee", new ColumnDef<>("payMinFee", r -> safeToString(r.getPayMinFee())));
        AGENT_ALLOWED.put("collectionRate", new ColumnDef<>("collectionRate", r -> safeToString(r.getCollectionRate())));
        AGENT_ALLOWED.put("collectionFixedFee", new ColumnDef<>("collectionFixedFee", r -> safeToString(r.getCollectionFixedFee())));
        AGENT_ALLOWED.put("collectionMaxFee", new ColumnDef<>("collectionMaxFee", r -> safeToString(r.getCollectionMaxFee())));
        AGENT_ALLOWED.put("collectionMinFee", new ColumnDef<>("collectionMinFee", r -> safeToString(r.getCollectionMinFee())));
        AGENT_ALLOWED.put("loginIps", new ColumnDef<>("loginIps", r -> safeToString(r.getLoginIps())));
        AGENT_ALLOWED.put("withdrawIps", new ColumnDef<>("withdrawIps", r -> safeToString(r.getWithdrawIps())));
        AGENT_ALLOWED.put("contactName", new ColumnDef<>("contactName", r -> safeToString(r.getContactName())));
        AGENT_ALLOWED.put("contactEmail", new ColumnDef<>("contactEmail", r -> safeToString(r.getContactEmail())));
        AGENT_ALLOWED.put("contactPhone", new ColumnDef<>("contactPhone", r -> safeToString(r.getContactPhone())));

        // agent withdrawal account, export column
        AGENT_ACCOUNT_ALLOWED.put("agentName", new ColumnDef<>("agentName", WithdrawalAccountsDto::getName));
        AGENT_ACCOUNT_ALLOWED.put("accountName", new ColumnDef<>("accountName", WithdrawalAccountsDto::getUserName));
        AGENT_ACCOUNT_ALLOWED.put("walletAddr", new ColumnDef<>("walletAddr", WithdrawalAccountsDto::getWalletAddr));
        AGENT_ACCOUNT_ALLOWED.put("status", new ColumnDef<>("status", r -> safeToEnable(r.getStatus())));
        AGENT_ACCOUNT_ALLOWED.put("createTime", new ColumnDef<>("createTime", r -> safeToString(r.getCreateTime())));
        AGENT_ACCOUNT_ALLOWED.put("createBy", new ColumnDef<>("createBy", r -> safeToString(r.getCreateBy())));

        // merchant withdrawal account, export column
        MERCHANT_ACCOUNT_ALLOWED.put("merchantName", new ColumnDef<>("merchantName", WithdrawalAccountsDto::getName));
        MERCHANT_ACCOUNT_ALLOWED.put("accountName", new ColumnDef<>("accountName", WithdrawalAccountsDto::getUserName));
        MERCHANT_ACCOUNT_ALLOWED.put("walletAddr", new ColumnDef<>("walletAddr", WithdrawalAccountsDto::getWalletAddr));
        MERCHANT_ACCOUNT_ALLOWED.put("status", new ColumnDef<>("status", r -> safeToEnable(r.getStatus())));
        MERCHANT_ACCOUNT_ALLOWED.put("createTime", new ColumnDef<>("createTime", r -> safeToString(r.getCreateTime())));
        MERCHANT_ACCOUNT_ALLOWED.put("createBy", new ColumnDef<>("createBy", r -> safeToString(r.getCreateBy())));
    }


    private static String safeToChannelInfo(List<ChannelDto> channelDtoList) {
        if (channelDtoList == null || channelDtoList.isEmpty()) return "";
        return channelDtoList.stream()
                .map(p -> "channelName:" + p.getChannelName())
                .collect(Collectors.joining(" | "));
    }

    private static String safeToEnable(Integer status) {

        if (CommonConstant.ENABLE_STATUS_DISABLE.equals(status)) {
            return "false";
        }

        if (CommonConstant.ENABLE_STATUS_ENABLE.equals(status)) {
            return "true";
        }
        return "";
    }

    private static String safeToPaymentType(String paymentType) {

        if (CommonConstant.PAYMENT_TYPE_APP.equals(paymentType)) {
            return "App";
        }

        if (CommonConstant.PAYMENT_TYPE_BANK.equals(paymentType)) {
            return "Bank";
        }
        return "";
    }

    private static String safeToSupportType(Integer supportType) {

        if (CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)) {
            return "collection";
        }

        if (CommonConstant.SUPPORT_TYPE_PAY.equals(supportType)) {
            return "pay";
        }

        if (CommonConstant.SUPPORT_TYPE_ALL.equals(supportType)) {
            return "collection/pay";
        }
        return "";
    }

    private static String safeToCurrency(List<PaymentDto> paymentDtoList) {
        if (paymentDtoList == null || paymentDtoList.isEmpty()) return "";
        return paymentDtoList.stream().map(PaymentDto::getCurrency).distinct().collect(Collectors.joining(","));
    }

    private static String safeToPaymentInfo(List<PaymentDto> paymentDtoList, List<Integer> supportTypes) {
        if (paymentDtoList == null || paymentDtoList.isEmpty()) return "";
        return paymentDtoList.stream()
                .filter(p -> supportTypes.contains(p.getSupportType()))
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
