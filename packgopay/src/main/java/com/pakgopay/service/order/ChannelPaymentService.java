package com.pakgopay.service.order;


import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.entity.TransactionInfo;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.ChannelsMapper;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.PaymentsMapper;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.PayOrderDto;
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

    @Autowired
    private PayOrderMapper payOrderMapper;

    /**
     * get available payment ids
     * @param channelIds channel ids
     * @param supportType orderType (Collection / Payout)
     * @param transactionInfo transaction info
     * @return payment ids
     * @throws PakGoPayException Business Exception
     */
    public Long getPaymentId(
            String channelIds, Integer supportType, TransactionInfo transactionInfo) throws PakGoPayException {
        log.info("getPaymentId start, get available payment id");
        // key:payment_id value:channel_id
        Map<Long, ChannelDto> paymentMap = new HashMap<>();
        // 1. get payment info list through channel ids and payment no
        List<PaymentDto> paymentDtoList = getPaymentInfosByChannel(transactionInfo.getPaymentNo(), channelIds, supportType, paymentMap);

        // 2. filter support currency payment
        paymentDtoList = filterSupportCurrencyPayments(paymentDtoList, transactionInfo.getCurrency());

        // 3. filter no limit payment infos
        paymentDtoList = filterNoLimitPayments(transactionInfo.getAmount(), paymentDtoList, supportType);

        // TODO xiaoyou 成功率投票
        PaymentDto paymentDto = paymentDtoList.get(0);
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

    /**
     * get payment infos by merchant's channel and payment no
     * @param paymentNo payment no
     * @param channelIds merchant's channel
     * @param supportType orderType (Collection / Payout)
     * @param paymentMap payment map channel (key: payment id value: channel info)
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

    /**
     * filter payment that not support this order currency
     * @param availablePayments available payments
     * @param currency currency type (example:VND,PKR,USD)
     * @return filtered payments
     * @throws PakGoPayException business Exception
     */
    private List<PaymentDto> filterSupportCurrencyPayments(List<PaymentDto> availablePayments, String currency) throws PakGoPayException {
        log.info("filterSupportCurrencyPayments start");
        availablePayments = availablePayments.stream().filter(payment -> {
            return payment.getCurrencyType().equals(currency);
        }).toList();
        if (availablePayments.isEmpty()) {
            log.error("availablePayments is empty");
            throw new PakGoPayException(ResultCode.PAYMENT_NOT_SUPPORT_CURRENCY, "channel is not support this currency: " + currency);
        }
        log.error("filterSupportCurrencyPayments end, availablePayments size {}", availablePayments.size());
        return availablePayments;
    }

    /**
     * filter payment that over limit daily/montly amount sum
     * @param amount order amount
     * @param paymentDtoList payment infos
     * @param supportType orderType (Collection / Payout)
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
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endTime = startTime.plusMonths(1);
        if (CommonConstant.SUPPORT_TYPE_COLLECTION.equals(supportType)) {
            // order type Collection
            List<CollectionOrderDto> collectionOrderDetailDtoList;
            try {
                collectionOrderDetailDtoList =
                        collectionOrderMapper.getCollectionOrderInfosByPaymentIds(enAblePaymentIds, startTime, endTime);
            } catch (Exception e) {
                log.error("collectionOrderMapper getCollectionOrderInfosByPaymentIds failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            if (collectionOrderDetailDtoList == null || collectionOrderDetailDtoList.isEmpty()) {
                log.error("getCurrentAmountSumByType collectionOrderDetailDtoList is empty");
                return;
            }

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
                        dto.getPaymentId(), dto.getAmount(), BigDecimal::add);
            }
        } else {
            // order type Payout
            List<PayOrderDto> payOrderDtoList;
            try {
                payOrderDtoList =
                        payOrderMapper.getPayOrderInfosByPaymentIds(enAblePaymentIds, startTime, endTime);
            } catch (Exception e) {
                log.error("payOrderMapper getPayOrderInfosByPaymentIds failed, message {}", e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }

            if (payOrderDtoList == null || payOrderDtoList.isEmpty()) {
                log.error("getCurrentAmountSumByType payOrderDtoList is empty");
                return;
            }

            for (PayOrderDto dto : payOrderDtoList) {
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
        }

        log.info("getCurrentAmountSumByType end, currentDayAmountSum: {}, currentMonthAmountSum: {}"
                , JSON.toJSONString(currentDayAmountSum), JSON.toJSONString(currentMonthAmountSum));
    }

    /**
     * get merchant's payment ids by channel isd
     * @param channelIdList channel ids
     * @param paymentMap payment map channel (key: payment id value: channel info)
     * @return payment ids
     * @throws PakGoPayException business Exception
     */
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

    public void calculateTransactionFee(TransactionInfo transactionInfo, OrderType orderType) {

        // TODO
    }
}
