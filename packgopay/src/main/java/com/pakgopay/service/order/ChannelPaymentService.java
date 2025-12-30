package com.pakgopay.service.order;


import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.entity.TransactionInfo;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.ChannelsMapper;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PaymentsMapper;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.PaymentDto;
import com.pakgopay.util.CommontUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChannelPaymentService {

    @Autowired
    private PaymentsMapper paymentsMapper;

    @Autowired
    private ChannelsMapper channelsMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    public Long getPaymentId(Integer paymentNo, BigDecimal amount, String channelIds, Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException {

        // key:payment_id value:channel_id
        Map<Long, ChannelDto> paymentMap = new HashMap<>();
        // 1. get payment info list through channel ids and payment no
        List<PaymentDto> paymentDtoList = getPaymentInfosByChannel(paymentNo, channelIds, supportType, paymentMap);

        // 2. filter no limit payment infos
        List<PaymentDto> availablePayments = filterNoLimitPayments(amount, paymentDtoList);

        // TODO xiaoyou 成功率投票
        PaymentDto paymentDto = availablePayments.get(0);
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

        return paymentDto.getPaymentId();
    }

    private List<PaymentDto> getPaymentInfosByChannel(
            Integer paymentNo, String channelIds, Integer supportType, Map<Long, ChannelDto> paymentMap)
            throws PakGoPayException {
        log.info("filterNoLimitPayments start, channelIds {}", channelIds);
        // 1. obtain merchant's channel id list
        if (!StringUtils.hasText(channelIds)) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        Set<Long> channelIdList = Arrays.stream(channelIds.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(Long::valueOf)
                .collect(Collectors.toSet());
        if (channelIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not channel");
        }

        // 2. obtain merchant's available payment ids by channel ids
        Set<Long> paymentIdList = getAssociatePaymentIdByChannel(channelIdList, paymentMap);

        // 3. obtain merchant's available payment infos by channel ids
        List<PaymentDto> paymentDtoList = paymentsMapper.
                findEnableInfoByPaymentNos(supportType, paymentNo, paymentIdList);
        if (paymentDtoList == null || paymentDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "Merchants have no available matching payments");
        }
        return paymentDtoList;
    }

    private List<PaymentDto> filterNoLimitPayments(BigDecimal amount, List<PaymentDto> paymentDtoList) throws PakGoPayException {
        log.info("filterNoLimitPayments start, paymentsDtoList size {}", paymentDtoList.size());

        // Filter orders by amount that match the maximum and minimum range of the channel.
        paymentDtoList = paymentDtoList.stream().filter(dto ->
                        // check payment min amount
                        amount.compareTo(dto.getPaymentMinAmount()) > 0
                                // check payment max amount
                                && amount.compareTo(dto.getPaymentMaxAmount()) < 0)
                .toList();
        if (paymentDtoList.isEmpty()) {
            throw new PakGoPayException(ResultCode.ORDER_AMOUNT_OVER_LIMIT);
        }

        // get current day and month, used amount
        List<Long> enAblePaymentIds = paymentDtoList.stream().map(PaymentDto::getPaymentId).toList();
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endTime = startTime.plusMonths(1);
        List<CollectionOrderDto> collectionOrderDetailDtoList =
                collectionOrderMapper.getCollectionOrderInfosByPaymentIds(enAblePaymentIds, startTime, endTime);
        Map<Long, BigDecimal> currentDayAmountSum = new HashMap<>();
        Map<Long, BigDecimal> currentMonthAmountSum = new HashMap<>();

        for (CollectionOrderDto dto : collectionOrderDetailDtoList) {
            if (dto.getPaymentId() == null || dto.getAmount() == null) {
                continue;
            }
            // current day data
            if (dto.getCreateTime().equals(today.atStartOfDay())) {
                currentDayAmountSum.merge(dto.getPaymentId(),
                        dto.getAmount(), BigDecimal::add
                );
            }
            // current month data
            currentMonthAmountSum.merge(
                    dto.getPaymentId(), dto.getAmount(), BigDecimal::add
            );
        }

        // filter no limit payment
        List<PaymentDto> availablePayments = paymentDtoList.stream().filter(dto ->
                        // compare daily limit amount
                        CommontUtil.safeAdd(currentDayAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(dto.getCollectionDailyLimit()) < 0
                                // compare monthly limit amount
                                && CommontUtil.safeAdd(currentMonthAmountSum.get(dto.getPaymentId()), amount).
                                compareTo(dto.getCollectionMonthlyLimit()) < 0
                                // check payment min amount
                                && amount.compareTo(dto.getPaymentMinAmount()) > 0
                                // check payment max amount
                                && amount.compareTo(dto.getPaymentMaxAmount()) < 0)
                .toList();
        if (availablePayments.isEmpty()) {
            throw new PakGoPayException(ResultCode.PAYMENT_AMOUNT_OVER_LIMIT, "Merchants have no available matching payments");
        }
        log.info("no limit payment info size {}", availablePayments.size());
        return availablePayments;
    }

    private Set<Long> getAssociatePaymentIdByChannel(Set<Long> channelIdList, Map<Long, ChannelDto> paymentMap) throws PakGoPayException {
        List<ChannelDto> channelInfos = channelsMapper.
                getPaymentIdsByChannelIds(channelIdList.stream().toList(), CommonConstant.ENABLE_STATUS_ENABLE);
        if (channelInfos.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }

        Set<Long> paymentIdList = new HashSet<>();
        channelInfos.forEach(dto -> {
            String ids = dto.getPaymentIds();
            if (!StringUtils.hasText(ids)) {
                return;
            }
            Set<Long> tempSet = Arrays.stream(ids.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).map(Long::valueOf)
                    .collect(Collectors.toSet());
            tempSet.forEach(id -> paymentMap.put(id, dto));
            paymentIdList.addAll(tempSet);
        });

        if (paymentIdList.isEmpty()) {
            throw new PakGoPayException(ResultCode.MERCHANT_HAS_NO_AVAILABLE_CHANNEL, "merchant has not payment");
        }
        return paymentIdList;
    }
}
