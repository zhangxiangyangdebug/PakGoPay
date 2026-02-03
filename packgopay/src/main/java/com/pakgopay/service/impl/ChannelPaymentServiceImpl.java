package com.pakgopay.service.impl;


import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.entity.channel.ChannelEntity;
import com.pakgopay.data.entity.channel.PaymentEntity;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.channel.ChannelResponse;
import com.pakgopay.data.response.channel.PaymentResponse;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.ExportFileUtils;
import com.pakgopay.util.PatchBuilderUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChannelPaymentServiceImpl implements ChannelPaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    // =====================
    // Payment selection
    // =====================
    /**
     * get available payment ids
     *
     * @param supportType     orderType (Collection / Payout)
     * @param transactionInfo transaction info
     * @return payment ids
     * @throws PakGoPayException Business Exception
     */
    @Override
    public Long selectPaymentId(Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException {
        log.info("selectPaymentId start, get available payment id");
        String resolvedChannelIds = resolveChannelIdsForMerchant(transactionInfo);
        log.info("selectPaymentId resolvedChannelIds, supportType={}, channelIds={}",
                CommonUtil.resolveSupportTypeLabel(supportType),
                resolvedChannelIds);

        // key:payment_id value:channel_id
        Map<Long, ChannelDto> paymentMap = new HashMap<>();
        // 1. get payment info list through channel ids and payment no
        List<PaymentDto> paymentDtoList = loadPaymentsByChannelIds(
                transactionInfo.getPaymentNo(), resolvedChannelIds, supportType, paymentMap);
        log.info("selectPaymentId loaded payments, size={}", paymentDtoList.size());

        // 2. filter support currency payment
        paymentDtoList = filterPaymentsByCurrency(paymentDtoList, transactionInfo.getCurrency());
        log.info("selectPaymentId currency filtered, currency={}, size={}", transactionInfo.getCurrency(), paymentDtoList.size());

        // 3. filter no limit payment infos
        paymentDtoList = filterPaymentsByLimits(transactionInfo.getAmount(), paymentDtoList, supportType);
        log.info("selectPaymentId limit filtered, amount={}, size={}", transactionInfo.getAmount(), paymentDtoList.size());

        PaymentDto paymentDto = selectPaymentByPerformance(paymentDtoList, supportType);
        log.info(
                "merchant payment search success, paymentId={}, paymentName={}",
                paymentDto.getPaymentId(),
                paymentDto.getPaymentName());

        // payment info
        transactionInfo.setPaymentId(paymentDto.getPaymentId());
        transactionInfo.setPaymentInfo(paymentDto);
        // channel info
        transactionInfo.setChannelId(paymentMap.get(paymentDto.getPaymentId()).getChannelId());
        transactionInfo.setChannelInfo(paymentMap.get(paymentDto.getPaymentId()));

        log.info("selectPaymentId success, id: {}", paymentDto.getPaymentId());
        return paymentDto.getPaymentId();
    }

    // =====================
    // Stats updates
    // =====================
    @Override
    public void updateChannelAndPaymentCounters(CollectionOrderDto order, TransactionStatus status) {
        // Update counters only for final status changes.
        boolean isSuccess = TransactionStatus.SUCCESS.equals(status);
        boolean isFailure = TransactionStatus.FAILED.equals(status)
                || TransactionStatus.EXPIRED.equals(status)
                || TransactionStatus.CANCELLED.equals(status);
        if (!isSuccess && !isFailure) {
            log.info("skip stats update, status={}", status == null ? null : status.getCode());
            return;
        }

        Long channelId = order.getChannelId();
        if (channelId != null) {
            ChannelDto channel = channelMapper.findByChannelId(channelId);
            if (channel != null) {
                long totalCount = defaultLong(channel.getTotalCount()) + 1L;
                long failCount = defaultLong(channel.getFailCount()) + (isFailure ? 1L : 0L);
                ChannelDto update = new ChannelDto();
                update.setChannelId(channelId);
                update.setTotalCount(totalCount);
                update.setFailCount(failCount);
                update.setSuccessRate(calculateSuccessRate(totalCount, failCount));
                update.setUpdateTime(System.currentTimeMillis() / 1000);
                channelMapper.updateByChannelId(update);
                log.info("channel stats updated, channelId={}, totalCount={}, failCount={}", channelId, totalCount, failCount);
            } else {
                log.warn("channel not found, channelId={}", channelId);
            }
        }

        Long paymentId = order.getPaymentId();
        if (paymentId != null) {
            PaymentDto payment = paymentMapper.findByPaymentId(paymentId);
            if (payment != null) {
                long orderQuantity = defaultLong(payment.getOrderQuantity()) + 1L;
                long successQuantity = defaultLong(payment.getSuccessQuantity()) + (isSuccess ? 1L : 0L);
                PaymentDto update = new PaymentDto();
                update.setPaymentId(paymentId);
                update.setOrderQuantity(orderQuantity);
                update.setSuccessQuantity(successQuantity);
                update.setUpdateTime(System.currentTimeMillis() / 1000);
                paymentMapper.updateByPaymentId(update);
                log.info("payment stats updated, paymentId={}, orderQuantity={}, successQuantity={}", paymentId, orderQuantity, successQuantity);
            } else {
                log.warn("payment not found, paymentId={}", paymentId);
            }
        }
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal calculateSuccessRate(long totalCount, long failCount) {
        if (totalCount <= 0) {
            return BigDecimal.ZERO;
        }
        long successCount = totalCount - failCount;
        return BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(totalCount), 6, RoundingMode.HALF_UP);
    }

    // =====================
    // Fee calculation
    // =====================
    @Override
    public void calculateTransactionFees(TransactionInfo transactionInfo, OrderType orderType) {
        BigDecimal amount = transactionInfo.getActualAmount() != null
                ? transactionInfo.getActualAmount()
                : transactionInfo.getAmount();
        // merchant fee
        BigDecimal fixedFee = null;
        BigDecimal rate = null;
        // get fixedFee and rate
        if (orderType.equals(OrderType.PAY_OUT_ORDER)) {
            fixedFee = transactionInfo.getMerchantInfo().getPayFixedFee();
            rate = transactionInfo.getMerchantInfo().getPayRate();
        } else {
            fixedFee = transactionInfo.getMerchantInfo().getCollectionFixedFee();
            rate = transactionInfo.getMerchantInfo().getCollectionRate();
        }

        calculateAgentFees(transactionInfo, orderType);

        CommonUtil.FeeCalcInput feeInput = new CommonUtil.FeeCalcInput();
        feeInput.amount = amount;
        feeInput.merchantRate = rate;
        feeInput.merchantFixed = fixedFee;
        feeInput.agent1Rate = transactionInfo.getAgent1Rate();
        feeInput.agent1Fixed = transactionInfo.getAgent1FixedFee();
        feeInput.agent2Rate = transactionInfo.getAgent2Rate();
        feeInput.agent2Fixed = transactionInfo.getAgent2FixedFee();
        feeInput.agent3Rate = transactionInfo.getAgent3Rate();
        feeInput.agent3Fixed = transactionInfo.getAgent3FixedFee();

        CommonUtil.FeeProfitResult feeProfit = CommonUtil.calculateTierProfits(feeInput);

        BigDecimal merchantFee = CommonUtil.defaultBigDecimal(feeProfit.merchantFee);
        if (amount != null && amount.compareTo(merchantFee) <= 0) {
            log.error("amount not support merchant fee, actualAmount={}, merchantFee={}", amount, merchantFee);
            throw new PakGoPayException(ResultCode.ORDER_PARAM_VALID, "amount not support merchant fee");
        }

        transactionInfo.setMerchantFee(merchantFee);
        transactionInfo.setAgent1Fee(feeProfit.agent1Profit);
        transactionInfo.setAgent2Fee(feeProfit.agent2Profit);
        transactionInfo.setAgent3Fee(feeProfit.agent3Profit);
        transactionInfo.setMerchantRate(rate);
        transactionInfo.setMerchantFixedFee(fixedFee);
    }

    private void calculateAgentFees(TransactionInfo transactionInfo, OrderType orderType) {
        String agentId = transactionInfo.getMerchantInfo().getParentId();
        if (agentId == null) {
            log.info("merchant has not agent");
            return;
        }

        List<AgentInfoDto> chains = new ArrayList<>();
        Set<String> visited = new HashSet<>(); // 防环

        AgentInfoDto current = loadAgentByUserId(agentId);
        if (current == null) {
            log.error("agent info is not exists, agentId {}", agentId);
            return;
        }

        Integer startLevel = current.getLevel();
        while (startLevel >= CommonConstant.AGENT_LEVEL_FIRST) {
            // preventing the formation of circular links
            if (current == null || visited.contains(current.getUserId())) {
                log.warn("userId is duplicate");
                break;
            } else {
                visited.add(current.getUserId());
            }
            // check agent enable status
            if (CommonConstant.ENABLE_STATUS_ENABLE.equals(current.getStatus())) {
                chains.add(current);
            } else {
                log.info("agent is not enable");
                break;
            }
            // check agent parent id
            if (!StringUtils.hasText(current.getParentId())) {
                log.info("agent has not parent agent");
                break;
            }
            // check agent level
            if (current.getLevel() != null && current.getLevel() <= 1) {
                log.info("agent level is {}", current.getLevel());
                break;
            }

            current = loadAgentByUserId(current.getParentId());
            startLevel--;
        }

        applyAgentFeesToTransaction(transactionInfo, orderType, chains);
    }

    /**
     * save agent's fee info
     *
     * @param transactionInfo transaction info
     * @param orderType       order type
     * @param chains          agent info list
     */
    private void applyAgentFeesToTransaction(TransactionInfo transactionInfo, OrderType orderType, List<AgentInfoDto> chains) {
        chains.forEach(info -> {

            BigDecimal rate = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayRate() : info.getCollectionRate();
            BigDecimal fixedFee = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayFixedFee() : info.getCollectionFixedFee();
            // First level agent
            if (CommonConstant.AGENT_LEVEL_FIRST.equals(info.getLevel())) {
                transactionInfo.setAgent1Rate(rate);
                transactionInfo.setAgent1FixedFee(fixedFee);
            }
            // Second level agent
            if (CommonConstant.AGENT_LEVEL_SECOND.equals(info.getLevel())) {
                transactionInfo.setAgent2Rate(rate);
                transactionInfo.setAgent2FixedFee(fixedFee);
            }
            // Third level agent
            if (CommonConstant.AGENT_LEVEL_THIRD.equals(info.getLevel())) {
                transactionInfo.setAgent3Rate(rate);
                transactionInfo.setAgent3FixedFee(fixedFee);
            }
        });
    }

    /**
     * get agent info by agent id
     *
     * @param agentId agent id
     * @return agent info
     */
    private AgentInfoDto loadAgentByUserId(String agentId) {
        try {
            return agentInfoMapper.findByUserId(agentId);
        } catch (Exception e) {
            log.error("agentInfoMapper findByUserId failed, agentId: {} message: {}", agentId, e.getMessage());
        }
        return null;
    }

    // =====================
    // Selection helpers
    // =====================
    private PaymentDto selectPaymentByPerformance(List<PaymentDto> paymentDtoList, Integer supportType) {
        if (paymentDtoList.size() == 1) {
            return paymentDtoList.getFirst();
        }
        Comparator<PaymentDto> comparator = Comparator
                .comparingDouble(this::computeSuccessRate)
                .thenComparingLong(dto -> defaultLong(dto.getSuccessQuantity()))
                .thenComparing(dto -> parsePaymentRate(dto, supportType), Comparator.reverseOrder());
        PaymentDto best = paymentDtoList.stream()
                .max(comparator)
                .orElse(paymentDtoList.getFirst());
        List<PaymentDto> topCandidates = paymentDtoList.stream()
                .filter(dto -> comparator.compare(dto, best) == 0)
                .toList();
        if (topCandidates.size() == 1) {
            return topCandidates.getFirst();
        }
        return topCandidates.get(new Random().nextInt(topCandidates.size()));
    }

    private double computeSuccessRate(PaymentDto dto) {
        long total = defaultLong(dto.getOrderQuantity());
        if (total <= 0) {
            return 0.0d;
        }
        return (double) defaultLong(dto.getSuccessQuantity()) / (double) total;
    }

    private BigDecimal parsePaymentRate(PaymentDto dto, Integer supportType) {
        String rate = CommonConstant.SUPPORT_TYPE_PAY.equals(supportType)
                ? dto.getPaymentPayRate()
                : dto.getPaymentCollectionRate();
        if (rate == null || rate.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rate.trim());
        } catch (NumberFormatException e) {
            log.warn("invalid payment rate: {}", rate);
            return BigDecimal.ZERO;
        }
    }

    private String resolveChannelIdsForMerchant(TransactionInfo transactionInfo) throws PakGoPayException {
        MerchantInfoDto merchantInfo = transactionInfo.getMerchantInfo();
        String resolvedChannelIds = merchantInfo == null ? null : merchantInfo.getChannelIds();
        if (StringUtils.hasText(resolvedChannelIds)) {
            return resolvedChannelIds;
        }

        String agentId = merchantInfo == null ? null : merchantInfo.getParentId();
        if (!StringUtils.hasText(agentId)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not agent channel");
        }

        AgentInfoDto agentInfo = merchantInfo.getCurrentAgentInfo();
        if (agentInfo == null || !StringUtils.hasText(agentInfo.getChannelIds())) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "agent has not channel");
        }

        log.info("merchant channelIds empty, use agent channelIds, agentId={}", agentId);
        return agentInfo.getChannelIds();
    }

    /**
     * get payment infos by merchant's channel and payment no
     *
     * @param paymentNo   payment no
     * @param channelIds  merchant's channel
     * @param supportType orderType (Collection / Payout)
     * @param paymentMap  payment map channel (key: payment id value: channel info)
     * @return paymentInfo
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> loadPaymentsByChannelIds(
            Integer paymentNo, String channelIds, Integer supportType, Map<Long, ChannelDto> paymentMap)
            throws PakGoPayException {
        log.info("loadPaymentsByChannelIds start, paymentNo={}, supportType={}, channelIds={}",
                paymentNo, CommonUtil.resolveSupportTypeLabel(supportType), channelIds);
        // 1. obtain merchant's channel id list
        if (!StringUtils.hasText(channelIds)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        List<Long> channelIdList = CommonUtil.parseIds(channelIds);
        if (channelIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }
        log.info("channelIds parsed, channelCount={}", channelIdList);

        // 2. obtain merchant's available payment ids by channel ids
        Set<Long> paymentIdList = collectPaymentIdsByChannelIds(channelIdList, paymentMap);
        log.info("paymentIds resolved by channelIds, paymentCount={}", paymentIdList);

        // 3. obtain merchant's available payment infos by channel ids
        List<PaymentDto> paymentDtoList = paymentMapper.
                findEnableInfoByPaymentNos(supportType, paymentNo, paymentIdList);
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "Merchants have no available matching payments");
        }
        log.info("payments loaded, paymentCount={}", paymentDtoList.size());
        paymentDtoList = filterPaymentsByEnableTime(paymentDtoList);
        log.info("payments filtered by enable time, paymentCount={}", paymentDtoList.size());
        log.info("payments summary, supportType={}, channels={}, payments={}, currencies={}",
                CommonUtil.resolveSupportTypeLabel(supportType),
                buildChannelSummary(paymentDtoList, paymentMap),
                buildPaymentSummary(paymentDtoList),
                paymentDtoList.stream()
                        .map(PaymentDto::getCurrency)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        return paymentDtoList;
    }

    private List<String> buildChannelSummary(List<PaymentDto> paymentDtoList, Map<Long, ChannelDto> paymentMap) {
        Map<Long, String> channelNames = new LinkedHashMap<>();
        for (PaymentDto payment : CommonUtil.safeList(paymentDtoList)) {
            ChannelDto channel = paymentMap.get(payment.getPaymentId());
            if (channel == null || channel.getChannelId() == null) {
                continue;
            }
            channelNames.putIfAbsent(channel.getChannelId(), channel.getChannelName());
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : channelNames.entrySet()) {
            result.add(entry.getKey() + ":" + entry.getValue());
        }
        return result;
    }

    private List<String> buildPaymentSummary(List<PaymentDto> paymentDtoList) {
        List<String> result = new ArrayList<>();
        for (PaymentDto payment : CommonUtil.safeList(paymentDtoList)) {
            if (payment == null || payment.getPaymentId() == null) {
                continue;
            }
            result.add(payment.getPaymentId() + ":" + payment.getPaymentName());
        }
        return result;
    }


    private List<PaymentDto> filterPaymentsByEnableTime(List<PaymentDto> paymentDtoList) throws PakGoPayException {
        // Filter payments by enableTimePeriod (format: HH:mm:ss,HH:mm:ss).
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        List<PaymentDto> availablePayments = paymentDtoList.stream()
                .filter(payment -> {
                    boolean allowed = isWithinEnableTimeWindow(now, payment.getEnableTimePeriod());
                    if (!allowed) {
                        log.warn("payment filtered by enableTimePeriod, paymentId={}, reason={}",
                                payment.getPaymentId(),
                                resolveEnableTimeRejectReason(now, payment.getEnableTimePeriod()));
                    }
                    return allowed;
                })
                .toList();
        if (availablePayments.isEmpty()) {
            log.warn("no available payments in enable time period, now={}", now);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "no available payments in time period");
        }
        return availablePayments;
    }

    private String resolveEnableTimeRejectReason(LocalTime now, String enableTimePeriod) {
        if (!StringUtils.hasText(enableTimePeriod)) {
            return "missing enableTimePeriod";
        }
        String[] parts = enableTimePeriod.split(",");
        if (parts.length != 2) {
            return "invalid enableTimePeriod format";
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            if (start.equals(end)) {
                return "all-day enabled";
            }
            if (start.isBefore(end)) {
                return "outside same-day window";
            }
            return "outside cross-midnight window";
        } catch (Exception e) {
            return "parse enableTimePeriod failed";
        }
    }

    private boolean isWithinEnableTimeWindow(LocalTime now, String enableTimePeriod) {
        if (!StringUtils.hasText(enableTimePeriod)) {
            return true;
        }
        String[] parts = enableTimePeriod.split(",");
        if (parts.length != 2) {
            log.warn("invalid enableTimePeriod format: {}", enableTimePeriod);
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            if (start.equals(end)) {
                // Equal start/end means all-day enabled.
                return true;
            }
            if (start.isBefore(end)) {
                // Same-day window.
                return !now.isBefore(start) && !now.isAfter(end);
            }
            // Cross-midnight window
            return !now.isBefore(start) || !now.isAfter(end);
        } catch (Exception e) {
            log.warn("parse enableTimePeriod failed: {}", enableTimePeriod);
            return false;
        }
    }

    /**
     * filter payment that not support this order currency
     *
     * @param availablePayments available payments
     * @param currency          currency type (example:VND,PKR,USD)
     * @return filtered payments
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> filterPaymentsByCurrency(List<PaymentDto> availablePayments, String currency) throws PakGoPayException {
        availablePayments = availablePayments.stream()
                .filter(payment -> payment.getCurrency().equals(currency)).toList();
        if (availablePayments.isEmpty()) {
            log.error("availablePayments is empty");
            throw new PakGoPayException(ResultCode.PAYMENT_NOT_SUPPORT_CURRENCY, "channel is not support this currency: " + currency);
        }
        return availablePayments;
    }

    /**
     * filter payment that over limit daily/montly amount sum
     *
     * @param amount         order amount
     * @param paymentDtoList payment infos
     * @param supportType    orderType (Collection / Payout)
     * @return filtered payments
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> filterPaymentsByLimits(BigDecimal amount, List<PaymentDto> paymentDtoList, Integer supportType) throws PakGoPayException {
        // Filter orders by amount that match the maximum and minimum range of the channel.
        paymentDtoList = paymentDtoList.stream().filter(dto ->
                        // check payment min amount
                {
                    boolean result = amount.compareTo(dto.getPaymentMinAmount()) >= 0
                            // check payment max amount
                            && amount.compareTo(dto.getPaymentMaxAmount()) <= 0;

                    if (!result) {
                        log.warn("paymentName: {}, amount max: {} min: {}, over limit amount: {}"
                                , dto.getPaymentName(), dto.getPaymentMaxAmount(), dto.getPaymentMinAmount(), amount);
                    }
                    return result;
                })
                .toList();
        if (paymentDtoList.isEmpty()) {
            log.error("paymentDtoList is empty, amount is over limit, amount: {}", amount);
            throw new PakGoPayException(ResultCode.ORDER_AMOUNT_OVER_LIMIT, "the amount over merchant's payment limit");
        }

        // get current day and month, used amount
        List<Long> enAblePaymentIds = paymentDtoList.stream().map(PaymentDto::getPaymentId).toList();
        Map<Long, BigDecimal> currentDayAmountSum = new HashMap<>();
        Map<Long, BigDecimal> currentMonthAmountSum = new HashMap<>();
        loadCurrentAmountSums(enAblePaymentIds, supportType, currentDayAmountSum, currentMonthAmountSum);

        // filter no limit payment
        List<PaymentDto> availablePayments = paymentDtoList.stream().filter(dto ->
                        // compare daily limit amount
                        CommonUtil.safeAdd(currentDayAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType) ? dto.getCollectionDailyLimit() : dto.getPayDailyLimit()) < 0
                                // compare monthly limit amount
                                && CommonUtil.safeAdd(currentMonthAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType) ? dto.getCollectionMonthlyLimit() : dto.getPayDailyLimit()) < 0
                                // check payment min amount
                                && amount.compareTo(dto.getPaymentMinAmount()) > 0
                                // check payment max amount
                                && amount.compareTo(dto.getPaymentMaxAmount()) < 0)
                .toList();
        if (availablePayments.isEmpty()) {
            throw new PakGoPayException(ResultCode.PAYMENT_AMOUNT_OVER_LIMIT, "Merchant‘s payments over daily/monthly limit");
        }
        return availablePayments;
    }

    /**
     * get current daily/monthly amount sum
     *
     * @param enAblePaymentIds      paymentId
     * @param supportType           orderType (Collection / Payout)
     * @param currentDayAmountSum   daily amount (key: paymentId value: amount sum)
     * @param currentMonthAmountSum monthly amount (key: paymentId value: amount sum)
     */
    private void loadCurrentAmountSums(
            List<Long> enAblePaymentIds, Integer supportType, Map<Long, BigDecimal> currentDayAmountSum,
            Map<Long, BigDecimal> currentMonthAmountSum) throws PakGoPayException {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        ZonedDateTime dayStart = today.atStartOfDay(zoneId);
        ZonedDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
        long monthStartTime = monthStart.toEpochSecond();
        long nextMonthStartTime = monthStart.plusMonths(1).toEpochSecond();
        if (CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)) {
            // order type Collection
            List<CollectionOrderDto> collectionOrderDetailDtoList;
            try {
                collectionOrderDetailDtoList =
                        collectionOrderMapper.getCollectionOrderInfosByPaymentIds(
                                enAblePaymentIds, monthStartTime, nextMonthStartTime);
            } catch (Exception e) {
                log.error("collectionOrderMapper getCollectionOrderInfosByPaymentIds failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            if (collectionOrderDetailDtoList == null || collectionOrderDetailDtoList.isEmpty()) {
                return;
            }

            accumulateAmountSums(collectionOrderDetailDtoList, dayStart,
                    currentDayAmountSum, currentMonthAmountSum, true);
        } else {
            // order type Payout
            List<PayOrderDto> payOrderDtoList;
            try {
                payOrderDtoList =
                        payOrderMapper.getPayOrderInfosByPaymentIds(
                                enAblePaymentIds, monthStartTime, nextMonthStartTime);
            } catch (Exception e) {
                log.error("payOrderMapper getPayOrderInfosByPaymentIds failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            if (payOrderDtoList == null || payOrderDtoList.isEmpty()) {
                return;
            }

            accumulateAmountSums(payOrderDtoList, dayStart,
                    currentDayAmountSum, currentMonthAmountSum, false);
        }

    }

    private void accumulateAmountSums(
            List<?> orderDtoList,
            ZonedDateTime dayStart,
            Map<Long, BigDecimal> currentDayAmountSum,
            Map<Long, BigDecimal> currentMonthAmountSum,
            boolean isCollection) {
        long dayStartTime = dayStart.toEpochSecond();
        long nextDayStartTime = dayStart.plusDays(1).toEpochSecond();
        // Aggregate per-payment daily/monthly totals; dto type depends on isCollection.
        for (Object dto : orderDtoList) {
            Long paymentId;
            BigDecimal amount;
            Long createTime;
            if (isCollection) {
                CollectionOrderDto orderDto = (CollectionOrderDto) dto;
                paymentId = orderDto.getPaymentId();
                amount = orderDto.getAmount();
                createTime = orderDto.getCreateTime();
            } else {
                PayOrderDto orderDto = (PayOrderDto) dto;
                paymentId = orderDto.getPaymentId();
                amount = orderDto.getAmount();
                createTime = orderDto.getCreateTime();
            }
            if (paymentId == null || amount == null || createTime == null) {
                continue;
            }
            // Add to daily sum if within today's range, always add to month sum.
            if (createTime >= dayStartTime && createTime < nextDayStartTime) {
                currentDayAmountSum.merge(paymentId, amount, BigDecimal::add);
            }
            currentMonthAmountSum.merge(paymentId, amount, BigDecimal::add);
        }
    }

    /**
     * get merchant's payment ids by channel isd
     *
     * @param channelIdList channel ids
     * @param paymentMap    payment map channel (key: payment id value: channel info)
     * @return payment ids
     * @throws PakGoPayException business Exception
     */
    private Set<Long> collectPaymentIdsByChannelIds(List<Long> channelIdList, Map<Long, ChannelDto> paymentMap) throws PakGoPayException {
        List<ChannelDto> channelInfos = channelMapper.
                getPaymentIdsByChannelIds(channelIdList, CommonConstant.ENABLE_STATUS_ENABLE);
        if (channelInfos.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }

        Set<Long> paymentIdList = new HashSet<>();
        channelInfos.forEach(dto -> {
            String ids = dto.getPaymentIds();
            if (!StringUtils.hasText(ids)) {
                return;
            }
            List<Long> tempSet = CommonUtil.parseIds(ids);
            tempSet.forEach(id -> paymentMap.put(id, dto));
            paymentIdList.addAll(tempSet);
        });

        if (paymentIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }
        return paymentIdList;
    }

    // =====================
    // Channel/payment management
    // =====================
    @Override
    public CommonResponse queryChannels(ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        ChannelResponse response = fetchChannelPage(channelQueryRequest);
        return CommonResponse.success(response);
    }

    private ChannelResponse fetchChannelPage(ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        ChannelEntity entity = new ChannelEntity();
        entity.setChannelId(channelQueryRequest.getChannelId());
        entity.setPaymentId(channelQueryRequest.getPaymentId());
        entity.setChannelName(channelQueryRequest.getChannelName());
        entity.setStatus(channelQueryRequest.getStatus());
        entity.setPageNo(channelQueryRequest.getPageNo());
        entity.setPageSize(channelQueryRequest.getPageSize());

        ChannelResponse response = new ChannelResponse();
        try {
            Integer totalNumber = channelMapper.countByQuery(entity);
            List<ChannelDto> channelDtoList = channelMapper.pageByQuery(entity);
            attachPaymentsToChannels(channelDtoList);

            response.setChannelDtoList(channelDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("channelsMapper channelsMapperData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    private void attachPaymentsToChannels(List<ChannelDto> channelDtoList) {
        Map<ChannelDto, List<Long>> channelPaymentIdsMap = new HashMap<>();
        Set<Long> allPaymentIds = new HashSet<>();

        for (ChannelDto channel : channelDtoList) {
            List<Long> ids = CommonUtil.parseIds(channel.getPaymentIds());
            channelPaymentIdsMap.put(channel, ids);
            allPaymentIds.addAll(ids);
        }

        Map<Long, PaymentDto> paymentMap = allPaymentIds.isEmpty()
                ? Collections.emptyMap()
                : paymentMapper.findByPaymentIds(new ArrayList<>(allPaymentIds)).stream()
                .filter(p -> p != null && p.getPaymentId() != null)
                .collect(Collectors.toMap(PaymentDto::getPaymentId, v -> v, (a, b) -> a));
        for (ChannelDto channel : channelDtoList) {
            List<Long> ids = channelPaymentIdsMap.getOrDefault(channel, Collections.emptyList());

            List<PaymentDto> list = channel.getPaymentDtoList();
            if (list == null) {
                list = new ArrayList<>();
                channel.setPaymentDtoList(list);
            }

            for (Long pid : ids) {
                PaymentDto p = paymentMap.get(pid);
                if (p != null) {
                    list.add(p);
                }
            }
        }
    }

    @Override
    public CommonResponse queryPayments(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
        PaymentResponse response = fetchPaymentPage(paymentQueryRequest);
        return CommonResponse.success(response);
    }

    private PaymentResponse fetchPaymentPage(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentName(paymentQueryRequest.getPaymentName());
        entity.setSupportType(paymentQueryRequest.getSupportType());
        entity.setPaymentType(paymentQueryRequest.getPaymentType());
        entity.setCurrency(paymentQueryRequest.getCurrency());
        entity.setStatus(paymentQueryRequest.getStatus());
        entity.setPageNo(paymentQueryRequest.getPageNo());
        entity.setPageSize(paymentQueryRequest.getPageSize());

        PaymentResponse response = new PaymentResponse();
        try {
            Integer totalNumber = paymentMapper.countByQuery(entity);
            List<PaymentDto> paymentDtoList = paymentMapper.pageByQuery(entity);

            response.setPaymentDtoList(paymentDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("paymentMapper paymentMapperData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    @Override
    public void exportChannels(ChannelQueryRequest channelQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<ChannelDto> colRes =
                ExportFileUtils.parseColumns(channelQueryRequest, ExportReportDataColumns.CHANNEL_ALLOWED);

        // 2) Init paging params
        channelQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        channelQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                channelQueryRequest,
                (req) -> fetchChannelPage(req).getChannelDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);
    }

    @Override
    public void exportPayments(PaymentQueryRequest paymentQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<PaymentDto> colRes =
                ExportFileUtils.parseColumns(paymentQueryRequest, ExportReportDataColumns.PAYMENT_ALLOWED);

        // 2) Init paging params
        paymentQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        paymentQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                paymentQueryRequest,
                (req) -> fetchPaymentPage(req).getPaymentDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.PAYMENT_EXPORT_FILE_NAME);
    }

    @Override
    public CommonResponse updateChannel(ChannelEditRequest channelEditRequest) throws PakGoPayException {
        ChannelDto channelDto = buildChannelUpdateDto(channelEditRequest);
        try {
            int ret = channelMapper.updateByChannelId(channelDto);
            log.info("updateChannel updateByChannelId done, channelId={}, ret={}", channelEditRequest.getChannelId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "channel not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("updateChannel updateByChannelId failed, channelId={}", channelEditRequest.getChannelId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto buildChannelUpdateDto(ChannelEditRequest channelEditRequest) throws PakGoPayException {
        ChannelDto dto = new ChannelDto();
        dto.setChannelId(PatchBuilderUtil.parseRequiredLong(channelEditRequest.getChannelId(), "channelId"));
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(channelEditRequest).to(dto)
                .str(channelEditRequest::getChannelName, dto::setChannelName)
                .str(channelEditRequest::getUserName, dto::setUpdateBy)
                .obj(channelEditRequest::getStatus, dto::setStatus)
                .ids(channelEditRequest::getPaymentIds, dto::setPaymentIds)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse updatePayment(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
        PaymentDto paymentDto = buildPaymentUpdateDto(paymentEditRequest);

        try {
            int ret = paymentMapper.updateByPaymentId(paymentDto);
            log.info("updatePayment updateByPaymentId done, paymentId={}, ret={}", paymentEditRequest.getPaymentId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "payment not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("updatePayment updateByChannelId failed, channelId={}", paymentEditRequest.getPaymentId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto buildPaymentUpdateDto(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(PatchBuilderUtil.parseRequiredLong(paymentEditRequest.getPaymentId(), "paymentId"));
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(paymentEditRequest).to(dto)
                .str(paymentEditRequest::getPaymentNo, dto::setPaymentNo)
                .str(paymentEditRequest::getPaymentName, dto::setPaymentName)
                .str(paymentEditRequest::getUserName, dto::setUpdateBy)
                .obj(paymentEditRequest::getStatus, dto::setStatus)
                .obj(paymentEditRequest::getSupportType, dto::setSupportType)
                .str(paymentEditRequest::getPaymentType, dto::setPaymentType)
                .str(paymentEditRequest::getBankName, dto::setBankName)
                .str(paymentEditRequest::getBankAccount, dto::setBankAccount)
                .str(paymentEditRequest::getBankUserName, dto::setBankUserName)
                .str(paymentEditRequest::getEnableTimePeriod, dto::setEnableTimePeriod)
                .str(paymentEditRequest::getPayInterfaceParam, dto::setPayInterfaceParam)
                .str(paymentEditRequest::getCollectionInterfaceParam, dto::setCollectionInterfaceParam)
                .obj(paymentEditRequest::getIsCheckoutCounter, dto::setIsCheckoutCounter)
                .obj(paymentEditRequest::getCollectionDailyLimit, dto::setCollectionDailyLimit)
                .obj(paymentEditRequest::getPayDailyLimit, dto::setPayDailyLimit)
                .obj(paymentEditRequest::getCollectionMonthlyLimit, dto::setCollectionMonthlyLimit)
                .obj(paymentEditRequest::getPayMonthlyLimit, dto::setPayMonthlyLimit)
                .str(paymentEditRequest::getPaymentRequestPayUrl, dto::setPaymentRequestPayUrl)
                .str(paymentEditRequest::getPaymentRequestCollectionUrl, dto::setPaymentRequestCollectionUrl)
                .str(paymentEditRequest::getPaymentPayRate, dto::setPaymentPayRate)
                .str(paymentEditRequest::getPaymentCollectionRate, dto::setPaymentCollectionRate)
                .str(paymentEditRequest::getPaymentCheckPayUrl, dto::setPaymentCheckPayUrl)
                .str(paymentEditRequest::getPaymentCheckCollectionUrl, dto::setPaymentCheckCollectionUrl)
                .obj(paymentEditRequest::getPaymentMaxAmount, dto::setPaymentMaxAmount)
                .obj(paymentEditRequest::getPaymentMinAmount, dto::setPaymentMinAmount)
                .str(paymentEditRequest::getIsThird, dto::setIsThird)
                .str(paymentEditRequest::getCollectionCallbackAddr, dto::setCollectionCallbackAddr)
                .str(paymentEditRequest::getPayCallbackAddr, dto::setPayCallbackAddr)
                .str(paymentEditRequest::getCheckoutCounterUrl, dto::setCheckoutCounterUrl)
                .str(paymentEditRequest::getCurrency, dto::setCurrency)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse createChannel(ChannelAddRequest channelAddRequest) throws PakGoPayException {
        ChannelDto channelDto = buildChannelCreateDto(channelAddRequest);
        try {
            int ret = channelMapper.insert(channelDto);
            log.info("createChannel insert done, ret={}", ret);
        } catch (Exception e) {
            log.error("createChannel insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto buildChannelCreateDto(ChannelAddRequest channelAddRequest) {
        ChannelDto dto = new ChannelDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<ChannelAddRequest, ChannelDto> builder = PatchBuilderUtil.from(channelAddRequest).to(dto)
                .str(channelAddRequest::getChannelName, dto::setChannelName)
                .obj(channelAddRequest::getStatus, dto::setStatus)
                .ids(channelAddRequest::getPaymentIds, dto::setPaymentIds)

                // 10) Meta Info
                .obj(channelAddRequest::getRemark, dto::setRemark)
                .obj(() -> now, dto::setCreateTime)
                .obj(() -> now, dto::setUpdateTime)
                .str(channelAddRequest::getUserName, dto::setCreateBy)
                .str(channelAddRequest::getUserName, dto::setUpdateBy);

        return builder.build();
    }

    @Override
    public CommonResponse createPayment(PaymentAddRequest paymentAddRequest) throws PakGoPayException {
        PaymentDto paymentDto = buildPaymentCreateDto(paymentAddRequest);
        try {
            int ret = paymentMapper.insert(paymentDto);
            log.info("createPayment insert done, ret={}", ret);
        } catch (Exception e) {
            log.error("createPayment insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto buildPaymentCreateDto(
            PaymentAddRequest paymentAddRequest) throws PakGoPayException {
        PaymentDto dto = new PaymentDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder = PatchBuilderUtil.from(paymentAddRequest).to(dto)

                // 1) Basic Info
                .str(paymentAddRequest::getPaymentNo, dto::setPaymentNo)
                .str(paymentAddRequest::getPaymentName, dto::setPaymentName)
                .str(paymentAddRequest::getCurrency, dto::setCurrency)
                .str(paymentAddRequest::getPaymentType, dto::setPaymentType)
                .str(paymentAddRequest::getIsThird, dto::setIsThird)

                // 2) Status & Capability
                .obj(paymentAddRequest::getStatus, dto::setStatus)
                .obj(paymentAddRequest::getSupportType, dto::setSupportType)
                .obj(paymentAddRequest::getIsCheckoutCounter, dto::setIsCheckoutCounter)
                .str(paymentAddRequest::getEnableTimePeriod, dto::setEnableTimePeriod)

                // 3) Amount Limits (optional)
                .obj(paymentAddRequest::getPaymentMaxAmount, dto::setPaymentMaxAmount)
                .obj(paymentAddRequest::getPaymentMinAmount, dto::setPaymentMinAmount)

                // 10) Meta Info
                .obj(paymentAddRequest::getRemark, dto::setRemark)
                .obj(() -> now, dto::setCreateTime)
                .obj(() -> now, dto::setUpdateTime)
                .str(paymentAddRequest::getUserName, dto::setCreateBy)
                .str(paymentAddRequest::getUserName, dto::setUpdateBy);

        dto.setOrderQuantity(0L);
        dto.setSuccessQuantity(0L);

        // supportType routing
        Integer supportType = paymentAddRequest.getSupportType();
        if (supportType == 0 || supportType == 2) {
            applyCollectionRequiredFields(builder, paymentAddRequest);
        }
        if (supportType == 1 || supportType == 2) {
            applyPayRequiredFields(builder, paymentAddRequest);
        }

        // Checkout counter requirement
        builder.ifTrue(Integer.valueOf(1).equals(paymentAddRequest.getIsCheckoutCounter()))
                .reqStr("checkoutCounterUrl", paymentAddRequest::getCheckoutCounterUrl, dto::setCheckoutCounterUrl);

        // Bank info requirement (no lambda, no swallowing)
        builder.ifTrue(CommonConstant.PAYMENT_TYPE_BANK.equals(paymentAddRequest.getPaymentType()))
                .reqStr("bankName", paymentAddRequest::getBankName, dto::setBankName)
                .reqStr("bankAccount", paymentAddRequest::getBankAccount, dto::setBankAccount)
                .reqStr("bankUserName", paymentAddRequest::getBankUserName, dto::setBankUserName)
                .endSkip();

        return builder.build();
    }

    private void applyPayRequiredFields(
            PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder, PaymentAddRequest req) throws PakGoPayException {
        PaymentDto dto = builder.dto();

        builder.reqObj("payDailyLimit", req::getPayDailyLimit, dto::setPayDailyLimit)
                .reqObj("payMonthlyLimit", req::getPayMonthlyLimit, dto::setPayMonthlyLimit)
                .reqStr("paymentPayRate", req::getPaymentPayRate, dto::setPaymentPayRate)
                .reqStr("paymentRequestPayUrl", req::getPaymentRequestPayUrl, dto::setPaymentRequestPayUrl)
                .reqStr("paymentCheckPayUrl", req::getPaymentCheckPayUrl, dto::setPaymentCheckPayUrl)
                .reqStr("payInterfaceParam", req::getPayInterfaceParam, dto::setPayInterfaceParam)
                .reqStr("payCallbackAddr", req::getPayCallbackAddr, dto::setPayCallbackAddr);
    }

    private void applyCollectionRequiredFields(
            PatchBuilderUtil<PaymentAddRequest, PaymentDto> builder, PaymentAddRequest req) throws PakGoPayException {
        PaymentDto dto = builder.dto();

        builder.reqObj("collectionDailyLimit", req::getCollectionDailyLimit, dto::setCollectionDailyLimit)
                .reqObj("collectionMonthlyLimit", req::getCollectionMonthlyLimit, dto::setCollectionMonthlyLimit)
                .reqStr("paymentCollectionRate", req::getPaymentCollectionRate, dto::setPaymentCollectionRate)
                .reqStr("paymentRequestCollectionUrl", req::getPaymentRequestCollectionUrl, dto::setPaymentRequestCollectionUrl)
                .reqStr("paymentCheckCollectionUrl", req::getPaymentCheckCollectionUrl, dto::setPaymentCheckCollectionUrl)
                .reqStr("collectionInterfaceParam", req::getCollectionInterfaceParam, dto::setCollectionInterfaceParam)
                .reqStr("collectionCallbackAddr", req::getCollectionCallbackAddr, dto::setCollectionCallbackAddr);
    }
}
