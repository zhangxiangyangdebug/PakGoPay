package com.pakgopay.service.channel.impl;


import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.channel.ChannelEditRequest;
import com.pakgopay.common.reqeust.channel.ChannelQueryRequest;
import com.pakgopay.common.reqeust.channel.PaymentEditRequest;
import com.pakgopay.common.reqeust.channel.PaymentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.channel.ChannelResponse;
import com.pakgopay.common.response.channel.PaymentResponse;
import com.pakgopay.entity.TransactionInfo;
import com.pakgopay.entity.channel.ChannelEntity;
import com.pakgopay.entity.channel.PaymentEntity;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.channel.ChannelPaymentService;
import com.pakgopay.service.report.ExportReportDataColumns;
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
import java.time.ZoneOffset;
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
     * @param channelIds      channel ids
     * @param supportType     orderType (Collection / Payout)
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

        List<Long> channelIdList = parseIds(channelIds);
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
        return paymentDtoList;
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
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Long startTime = today
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
        Long endTime = today
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
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
            List<Long> tempSet = parseIds(ids);
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
            List<Long> ids = parseIds(channel.getPaymentIds());
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
        entity.setPaymentNo(paymentQueryRequest.getPaymentNo());
        entity.setPaymentName(paymentQueryRequest.getPaymentName());
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

        return PatchBuilderUtil.from(channelEditRequest).to(dto)
                .str(channelEditRequest::getChannelName, dto::setChannelName)
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

        return PatchBuilderUtil.from(paymentEditRequest).to(dto)
                .str(paymentEditRequest::getPaymentNo, dto::setPaymentNo)
                .str(paymentEditRequest::getPaymentName, dto::setPaymentName)
                .obj(paymentEditRequest::getStatus, dto::setStatus)
                .obj(paymentEditRequest::getSupportType, dto::setSupportType)
                .str(paymentEditRequest::getPaymentType, dto::setPaymentType)
                .str(paymentEditRequest::getEnableTimePeriod, dto::setEnableTimePeriod)
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
                .str(paymentEditRequest::getIsThird, dto::setIsThird)
                .str(paymentEditRequest::getCollectionCallbackAddr, dto::setCollectionCallbackAddr)
                .str(paymentEditRequest::getPayCallbackAddr, dto::setPayCallbackAddr)
                .str(paymentEditRequest::getCheckoutCounterUrl, dto::setCheckoutCounterUrl)
                .str(paymentEditRequest::getCurrency, dto::setCurrency)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    private List<Long> parseIds(String csv) {
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
}
