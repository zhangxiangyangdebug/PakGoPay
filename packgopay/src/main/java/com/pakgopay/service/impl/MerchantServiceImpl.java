package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.merchant.MerchantEntity;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.merchant.MerchantResponse;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.ChannelMapper;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.PaymentMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PaymentDto;
import com.pakgopay.service.MerchantService;
import com.pakgopay.util.CommontUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MerchantServiceImpl implements MerchantService {

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private AgentInfoMapper agentInfoMapper;


    @Override
    public CommonResponse queryMerchant(MerchantQueryRequest merchantQueryRequest) {
        log.info("queryMerchant start");
        MerchantResponse response = queryMerchantData(merchantQueryRequest);
        log.info("queryMerchant end");
        return CommonResponse.success(response);
    }

    private MerchantResponse queryMerchantData(MerchantQueryRequest merchantQueryRequest) throws PakGoPayException {
        log.info("queryAgentData start");
        MerchantEntity entity = new MerchantEntity();
        entity.setMerchantName(merchantQueryRequest.getMerchantName());
        entity.setMerchantUserName(merchantQueryRequest.getMerchantUserName());
        entity.setMerchantUserId(merchantQueryRequest.getMerchantUserId());
        entity.setStatus(merchantQueryRequest.getStatus());
        entity.setParentAgentId(merchantQueryRequest.getParentAgentId());
        entity.setPageNo(merchantQueryRequest.getPageNo());
        entity.setPageSize(merchantQueryRequest.getPageSize());

        MerchantResponse response = new MerchantResponse();
        try {
            Integer totalNumber = merchantInfoMapper.countByQuery(entity);
            List<MerchantInfoDto> merchantInfoDtoList = merchantInfoMapper.pageByQuery(entity);
            getMerchantDetailInfo(merchantInfoDtoList);

            response.setMerchantInfoDtoList(merchantInfoDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("agentInfoMapper queryAgentData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryAgentData end");
        return response;
    }

    private void getMerchantDetailInfo(List<MerchantInfoDto> merchantInfoDtoList) {

        List<ChannelDto> channelDtoList = channelMapper.getAllChannels();
        List<PaymentDto> paymentDtoList = paymentMapper.getAllPayments();
        List<AgentInfoDto> agentInfoDtoList = agentInfoMapper.getAllAgentInfo();

        // Build quick lookup maps for O(1) access
        Map<String, AgentInfoDto> agentByUserIdMap = safeList(agentInfoDtoList).stream()
                .collect(Collectors.toMap(AgentInfoDto::getUserId, Function.identity(), (a, b) -> a));

        Map<Long, ChannelDto> channelByIdMap = safeList(channelDtoList).stream()
                .collect(Collectors.toMap(ChannelDto::getChannelId, Function.identity(), (a, b) -> a));

        Map<Long, PaymentDto> paymentByIdMap = safeList(paymentDtoList).stream()
                .collect(Collectors.toMap(PaymentDto::getPaymentId, Function.identity(), (a, b) -> a));

        for (MerchantInfoDto merchant : merchantInfoDtoList) {
            if (merchant == null) {
                continue;
            }

            // -------------------------
            // 1) Build agent chain list
            // -------------------------
            List<AgentInfoDto> agentChain = buildAgentChain(agentByUserIdMap, merchant.getParentId());
            merchant.setAgentInfos(agentChain);

            List<Long> channelIds;
            if (merchant.getParentId() != null && !merchant.getParentId().isBlank()) {
                // Merchant has an agent, use agent's channelIds
                AgentInfoDto parentAgent = agentByUserIdMap.get(merchant.getParentId());
                channelIds = CommontUtil.parseIds(parentAgent == null ? null : parentAgent.getChannelIds());
            } else {
                // No agent, use merchant's own channelIds
                channelIds = CommontUtil.parseIds(merchant.getChannelIds());
            }

            List<String> currencies = resolveCurrenciesByChannelIds(channelIds, channelByIdMap, paymentByIdMap);
            merchant.setCurrencyList(currencies);
        }
    }
    /**
     * Build agent chain list: [parentAgent, upperAgent]
     * Upper agent is defined as parentAgent.parentId (one level up).
     */
    private static List<AgentInfoDto> buildAgentChain(Map<String, AgentInfoDto> agentByUserId, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return Collections.emptyList();
        }

        List<AgentInfoDto> chain = new ArrayList<>(2);

        AgentInfoDto parent = agentByUserId.get(parentId);
        if (parent != null) {
            chain.add(parent);

            // Upper agent: parent's parentId
            String upperId = parent.getParentId();
            if (upperId != null && !upperId.isBlank()) {
                AgentInfoDto upper = agentByUserId.get(upperId);
                if (upper != null) {
                    chain.add(upper);
                }
            }
        }

        return chain;
    }

    /**
     * Resolve supported currencies by channelIds:
     * channelIds -> channels -> paymentIds -> payments -> currency
     * Returns a de-duplicated list (preserves insertion order).
     */
    private static List<String> resolveCurrenciesByChannelIds(
            List<Long> channelIds,
            Map<Long, ChannelDto> channelById,
            Map<Long, PaymentDto> paymentById
    ) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Use LinkedHashSet to keep stable order while de-duplicating
        Set<String> currencySet = new LinkedHashSet<>();
        for (Long channelId : channelIds) {
            ChannelDto channel = channelById.get(channelId);
            if (channel == null) continue;

            List<Long> paymentIds = CommontUtil.parseIds(channel.getPaymentIds());
            if (paymentIds.isEmpty()) continue;

            for (Long pid : paymentIds) {
                PaymentDto payment = paymentById.get(pid);
                if (payment == null) continue;

                String currency = payment.getCurrency();
                if (currency != null && !currency.isBlank()) {
                    currencySet.add(currency.trim());
                }
            }
        }
        return new ArrayList<>(currencySet);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
