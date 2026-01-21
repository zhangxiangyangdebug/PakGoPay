package com.pakgopay.service.impl;


import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.channel.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.channel.ChannelResponse;
import com.pakgopay.data.response.channel.PaymentResponse;
import com.pakgopay.data.entity.TransactionInfo;
import com.pakgopay.data.entity.channel.ChannelEntity;
import com.pakgopay.data.entity.channel.PaymentEntity;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.ChannelPaymentService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.util.CommontUtil;
import com.pakgopay.util.ExportFileUtils;
import com.pakgopay.util.PatchBuilderUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
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

    /**
     * get available payment ids
     *
     * @param supportType     orderType (Collection / Payout)
     * @param transactionInfo transaction info
     * @return payment ids
     * @throws PakGoPayException Business Exception
     */
    public Long getPaymentId(Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException {
        log.info("getPaymentId start, get available payment id");
        String resolvedChannelIds = resolveChannelIds(transactionInfo);

        // key:payment_id value:channel_id
        Map<Long, ChannelDto> paymentMap = new HashMap<>();
        // 1. get payment info list through channel ids and payment no
        List<PaymentDto> paymentDtoList = getPaymentInfosByChannel(
                transactionInfo.getPaymentNo(), resolvedChannelIds, supportType, paymentMap);

        // 2. filter support currency payment
        paymentDtoList = filterSupportCurrencyPayments(paymentDtoList, transactionInfo.getCurrency());

        // 3. filter no limit payment infos
        paymentDtoList = filterNoLimitPayments(transactionInfo.getAmount(), paymentDtoList, supportType);

        PaymentDto paymentDto = selectBySuccessRate(paymentDtoList, supportType);
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

        log.info("getPayment id success, id: {}", paymentDto.getPaymentId());
        return paymentDto.getPaymentId();
    }

    private PaymentDto selectBySuccessRate(List<PaymentDto> paymentDtoList, Integer supportType) {
        if (paymentDtoList.size() == 1) {
            return paymentDtoList.getFirst();
        }
        Comparator<PaymentDto> comparator = Comparator
                .comparingDouble(this::successRate)
                .thenComparing(PaymentDto::getSuccessQuantity)
                .thenComparing(dto -> resolveRate(dto, supportType), Comparator.reverseOrder());
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

    private double successRate(PaymentDto dto) {
        long total = dto.getOrderQuantity();
        if (total <= 0) {
            return 0.0d;
        }
        return (double) dto.getSuccessQuantity() / (double) total;
    }

    private BigDecimal resolveRate(PaymentDto dto, Integer supportType) {
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

    private String resolveChannelIds(TransactionInfo transactionInfo) throws PakGoPayException {
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
    private List<PaymentDto> getPaymentInfosByChannel(
            Integer paymentNo, String channelIds, Integer supportType, Map<Long, ChannelDto> paymentMap)
            throws PakGoPayException {
        log.info("filterNoLimitPayments start, channelIds {}", channelIds);
        // 1. obtain merchant's channel id list
        if (!StringUtils.hasText(channelIds)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        List<Long> channelIdList = CommontUtil.parseIds(channelIds);
        if (channelIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        // 2. obtain merchant's available payment ids by channel ids
        Set<Long> paymentIdList = getAssociatePaymentIdByChannel(channelIdList, paymentMap);

        // 3. obtain merchant's available payment infos by channel ids
        List<PaymentDto> paymentDtoList = paymentMapper.
                findEnableInfoByPaymentNos(supportType, paymentNo, paymentIdList);
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "Merchants have no available matching payments");
        }
        paymentDtoList = filterEnableTimePayments(paymentDtoList);
        return paymentDtoList;
    }

    private List<PaymentDto> filterEnableTimePayments(List<PaymentDto> paymentDtoList) throws PakGoPayException {
        // Filter payments by enableTimePeriod (format: HH:mm:ss,HH:mm:ss).
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        List<PaymentDto> availablePayments = paymentDtoList.stream()
                .filter(payment -> isWithinEnableTime(now, payment.getEnableTimePeriod()))
                .toList();
        if (availablePayments.isEmpty()) {
            log.warn("no available payments in enable time period, now={}", now);
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "no available payments in time period");
        }
        log.info("filterEnableTimePayments end, available size {}", availablePayments.size());
        return availablePayments;
    }

    private boolean isWithinEnableTime(LocalTime now, String enableTimePeriod) {
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
    private List<PaymentDto> filterSupportCurrencyPayments(List<PaymentDto> availablePayments, String currency) throws PakGoPayException {
        log.info("filterSupportCurrencyPayments start");
        availablePayments = availablePayments.stream()
                .filter(payment -> payment.getCurrency().equals(currency)).toList();
        if (availablePayments.isEmpty()) {
            log.error("availablePayments is empty");
            throw new PakGoPayException(ResultCode.PAYMENT_NOT_SUPPORT_CURRENCY, "channel is not support this currency: " + currency);
        }
        log.error("filterSupportCurrencyPayments end, availablePayments size {}", availablePayments.size());
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
    private List<PaymentDto> filterNoLimitPayments(BigDecimal amount, List<PaymentDto> paymentDtoList, Integer supportType) throws PakGoPayException {
        log.info("filterNoLimitPayments start, paymentsDtoList size {}", paymentDtoList.size());

        // Filter orders by amount that match the maximum and minimum range of the channel.
        paymentDtoList = paymentDtoList.stream().filter(dto ->
                        // check payment min amount
                {
                    boolean result = amount.compareTo(dto.getPaymentMinAmount()) > 0
                            // check payment max amount
                            && amount.compareTo(dto.getPaymentMaxAmount()) < 0;

                    if (!result) {
                        log.warn("paymentId: {}, amount max: {} min: {}, over limit amount: {}"
                                , dto.getPaymentId(), dto.getPaymentMaxAmount(), dto.getPaymentMinAmount(), amount);
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
        getCurrentAmountSumByType(enAblePaymentIds, supportType, currentDayAmountSum, currentMonthAmountSum);

        // filter no limit payment
        List<PaymentDto> availablePayments = paymentDtoList.stream().filter(dto ->
                        // compare daily limit amount
                        CommontUtil.safeAdd(currentDayAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType) ? dto.getCollectionDailyLimit() : dto.getPayDailyLimit()) < 0
                                // compare monthly limit amount
                                && CommontUtil.safeAdd(currentMonthAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType) ? dto.getCollectionMonthlyLimit() : dto.getPayDailyLimit()) < 0
                                // check payment min amount
                                && amount.compareTo(dto.getPaymentMinAmount()) > 0
                                // check payment max amount
                                && amount.compareTo(dto.getPaymentMaxAmount()) < 0)
                .toList();
        if (availablePayments.isEmpty()) {
            throw new PakGoPayException(ResultCode.PAYMENT_AMOUNT_OVER_LIMIT, "Merchant‘s payments over daily/monthly limit");
        }
        log.info("no limit payment info size {}", availablePayments.size());
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
    private void getCurrentAmountSumByType(
            List<Long> enAblePaymentIds, Integer supportType, Map<Long, BigDecimal> currentDayAmountSum,
            Map<Long, BigDecimal> currentMonthAmountSum) throws PakGoPayException {
        log.info("getCurrentAmountSumByType start");
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
                log.info("getCurrentAmountSumByType collectionOrderDetailDtoList is empty");
                return;
            }

            accumulateAmountSum(collectionOrderDetailDtoList, dayStart,
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
                log.info("getCurrentAmountSumByType payOrderDtoList is empty");
                return;
            }

            accumulateAmountSum(payOrderDtoList, dayStart,
                    currentDayAmountSum, currentMonthAmountSum, false);
        }

        log.info("getCurrentAmountSumByType end, currentDayAmountSum: {}, currentMonthAmountSum: {}"
                , JSON.toJSONString(currentDayAmountSum), JSON.toJSONString(currentMonthAmountSum));
    }

    private void accumulateAmountSum(
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
    private Set<Long> getAssociatePaymentIdByChannel(List<Long> channelIdList, Map<Long, ChannelDto> paymentMap) throws PakGoPayException {
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
            List<Long> tempSet = CommontUtil.parseIds(ids);
            tempSet.forEach(id -> paymentMap.put(id, dto));
            paymentIdList.addAll(tempSet);
        });

        if (paymentIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }
        return paymentIdList;
    }

    public void calculateTransactionFee(TransactionInfo transactionInfo, OrderType orderType) {
        BigDecimal amount = transactionInfo.getAmount();
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

        BigDecimal transactionFee = BigDecimal.ZERO;
        // fixed fee
        if (fixedFee != null && !fixedFee.equals(BigDecimal.ZERO)) {
            transactionFee = CommontUtil.safeAdd(transactionFee, fixedFee);
        }

        // percentage rate
        if (rate != null && !rate.equals(BigDecimal.ZERO)) {
            transactionFee = CommontUtil.safeAdd(transactionFee, CommontUtil.calculate(amount, rate, 6));
        }

        calculateAgentFee(transactionInfo, orderType);

        transactionInfo.setMerchantFee(transactionFee);
        transactionInfo.setMerchantRate(rate);
        transactionInfo.setMerchantFixedFee(fixedFee);
    }

    private void calculateAgentFee(TransactionInfo transactionInfo, OrderType orderType) {
        log.info("calculateAgentFee start");
        String agentId = transactionInfo.getMerchantInfo().getParentId();
        if (agentId == null) {
            log.info("merchant has not agent");
            return;
        }

        List<AgentInfoDto> chains = new ArrayList<>();
        Set<String> visited = new HashSet<>(); // 防环

        // 1) 先查当前用户，拿到起始 level
        AgentInfoDto current = getAgentInfoByAgentId(agentId);
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

            current = getAgentInfoByAgentId(current.getParentId());
            startLevel--;
        }

        setAgentInfoForTransaction(transactionInfo, orderType, chains);
        log.info("calculateAgentFee end");
    }

    /**
     * save agent's fee info
     *
     * @param transactionInfo transaction info
     * @param orderType       order type
     * @param chains          agent info list
     */
    private void setAgentInfoForTransaction(TransactionInfo transactionInfo, OrderType orderType, List<AgentInfoDto> chains) {
        BigDecimal amount = transactionInfo.getAmount();
        chains.forEach(info -> {

            BigDecimal rate = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayRate() : info.getCollectionRate();
            BigDecimal fixedFee = OrderType.PAY_OUT_ORDER.equals(
                    orderType) ? info.getPayFixedFee() : info.getCollectionFixedFee();
            BigDecimal agentFee = CommontUtil.calculate(
                    amount, OrderType.PAY_OUT_ORDER.equals(
                            orderType) ? info.getPayRate() : info.getCollectionRate(), 6);
            // First level agent
            if (CommonConstant.AGENT_LEVEL_FIRST.equals(info.getLevel())) {
                transactionInfo.setAgent1Rate(rate);
                transactionInfo.setAgent1FixedFee(fixedFee);
                transactionInfo.setAgent1Fee(agentFee);
            }
            // Second level agent
            if (CommonConstant.AGENT_LEVEL_SECOND.equals(info.getLevel())) {
                transactionInfo.setAgent2Rate(rate);
                transactionInfo.setAgent2FixedFee(fixedFee);
                transactionInfo.setAgent2Fee(agentFee);
            }
            // Third level agent
            if (CommonConstant.AGENT_LEVEL_THIRD.equals(info.getLevel())) {
                transactionInfo.setAgent3Rate(rate);
                transactionInfo.setAgent3FixedFee(fixedFee);
                transactionInfo.setAgent3Fee(agentFee);
            }
        });
    }

    /**
     * get agent info by agent id
     *
     * @param agentId agent id
     * @return agent info
     */
    private AgentInfoDto getAgentInfoByAgentId(String agentId) {
        try {
            return agentInfoMapper.findByUserId(agentId);
        } catch (Exception e) {
            log.error("agentInfoMapper findByUserId failed, agentId: {} message: {}", agentId, e.getMessage());
        }
        return null;
    }

    public CommonResponse queryChannel(@Valid ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        log.info("queryChannel start");
        ChannelResponse response = queryChannelData(channelQueryRequest);
        log.info("queryChannel end");
        return CommonResponse.success(response);
    }

    private ChannelResponse queryChannelData(ChannelQueryRequest channelQueryRequest) throws PakGoPayException {
        log.info("queryChannelData start");
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
            getPaymentInfoUnderChannel(channelDtoList);

            response.setChannelDtoList(channelDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("channelsMapper channelsMapperData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryChannelData end");
        return response;
    }

    private void getPaymentInfoUnderChannel(List<ChannelDto> channelDtoList) {
        log.info("getPaymentInfoUnderChannel start");
        Map<ChannelDto, List<Long>> channelPaymentIdsMap = new HashMap<>();
        Set<Long> allPaymentIds = new HashSet<>();

        for (ChannelDto channel : channelDtoList) {
            List<Long> ids = CommontUtil.parseIds(channel.getPaymentIds());
            channelPaymentIdsMap.put(channel, ids);
            allPaymentIds.addAll(ids);
        }

        Map<Long, PaymentDto> paymentMap = allPaymentIds.isEmpty()
                ? Collections.emptyMap()
                : paymentMapper.findByPaymentIds(new ArrayList<>(allPaymentIds)).stream()
                .filter(p -> p != null && p.getPaymentId() != null)
                .collect(Collectors.toMap(PaymentDto::getPaymentId, v -> v, (a, b) -> a));
        log.info("findByPaymentIds paymentMap size: {}", paymentMap.size());
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
        log.info("getPaymentInfoUnderChannel end");
    }

    public CommonResponse queryPayment(@Valid PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
        log.info("queryPayment start");
        PaymentResponse response = queryPaymentData(paymentQueryRequest);
        log.info("queryPayment end");
        return CommonResponse.success(response);
    }

    private PaymentResponse queryPaymentData(PaymentQueryRequest paymentQueryRequest) throws PakGoPayException {
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
    public void exportChannel(ChannelQueryRequest channelQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportChannel start");

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
                (req) -> queryChannelData(req).getChannelDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);

        log.info("exportChannel end");
    }

    @Override
    public void exportPayment(PaymentQueryRequest paymentQueryRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportPayment start");

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
                (req) -> queryPaymentData(req).getPaymentDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.PAYMENT_EXPORT_FILE_NAME);

        log.info("exportPayment end");
    }

    @Override
    public CommonResponse editChannel(ChannelEditRequest channelEditRequest) throws PakGoPayException {
        log.info("editChannel start, channelId={}", channelEditRequest.getChannelId());

        ChannelDto channelDto = checkAndGenerateChannelDto(channelEditRequest);
        try {
            int ret = channelMapper.updateByChannelId(channelDto);
            log.info("editChannel updateByChannelId done, channelId={}, ret={}", channelEditRequest.getChannelId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "channel not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editChannel updateByChannelId failed, channelId={}", channelEditRequest.getChannelId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editChannel end, channelId={}", channelEditRequest.getChannelId());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto checkAndGenerateChannelDto(ChannelEditRequest channelEditRequest) throws PakGoPayException {
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
    public CommonResponse editPayment(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
        log.info("editPayment start, paymentId={}", paymentEditRequest.getPaymentId());

        PaymentDto paymentDto = checkAndGeneratePaymentDto(paymentEditRequest);

        try {
            int ret = paymentMapper.updateByPaymentId(paymentDto);
            log.info("editPayment updateByPaymentId done, paymentId={}, ret={}", paymentEditRequest.getPaymentId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "payment not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editPayment updateByChannelId failed, channelId={}", paymentEditRequest.getPaymentId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editPayment end, channelId={}", paymentEditRequest.getPaymentId());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto checkAndGeneratePaymentDto(PaymentEditRequest paymentEditRequest) throws PakGoPayException {
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
    public CommonResponse addChannel(ChannelAddRequest channelAddRequest) throws PakGoPayException {
        log.info("addChannel start");

        ChannelDto channelDto = checkAndGenerateChannelDtoForAdd(channelAddRequest);
        try {
            int ret = channelMapper.insert(channelDto);
            log.info("addChannel insert done, ret={}", ret);
        } catch (Exception e) {
            log.error("addChannel insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("addChannel end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private ChannelDto checkAndGenerateChannelDtoForAdd(ChannelAddRequest channelAddRequest) {
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
    public CommonResponse addPayment(PaymentAddRequest paymentAddRequest) throws PakGoPayException {
        log.info("addPayment start");

        PaymentDto paymentDto = checkAndGeneratePaymentDtoForAdd(paymentAddRequest);
        try {
            int ret = paymentMapper.insert(paymentDto);
            log.info("addPayment insert done, ret={}", ret);
        } catch (Exception e) {
            log.error("addPayment insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("addPayment end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private PaymentDto checkAndGeneratePaymentDtoForAdd(
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
            applyCollectRequired(builder, paymentAddRequest);
        }
        if (supportType == 1 || supportType == 2) {
            applyPayRequired(builder, paymentAddRequest);
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

    private void applyPayRequired(
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

    private void applyCollectRequired(
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
