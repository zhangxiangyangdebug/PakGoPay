package com.pakgopay.service.agent.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.agent.AgentResponse;
import com.pakgopay.entity.agent.AgentInfoEntity;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.ChannelMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.service.agent.AgentService;
import com.pakgopay.util.CommontUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Override
    public CommonResponse queryAgent(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        log.info("queryAgent start");
        AgentResponse response = queryAgentData(agentQueryRequest);
        log.info("queryAgent end");
        return CommonResponse.success(response);
    }

    private AgentResponse queryAgentData(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        log.info("queryAgentData start");
        AgentInfoEntity entity = new AgentInfoEntity();
        entity.setAgentName(agentQueryRequest.getAgentName());
        entity.setUserName(agentQueryRequest.getAccountName());
        entity.setStatus(agentQueryRequest.getStatus());
        entity.setPageNo(agentQueryRequest.getPageNo());
        entity.setPageSize(agentQueryRequest.getPageSize());

        AgentResponse response = new AgentResponse();
        try {
            Integer totalNumber = agentInfoMapper.countByQuery(entity);
            List<AgentInfoDto> agentInfoDtoList = agentInfoMapper.pageByQuery(entity);
            getAgentDetailInfo(agentInfoDtoList);

            response.setAgentInfoDtoList(agentInfoDtoList);
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

    private void getAgentDetailInfo(List<AgentInfoDto> agentInfoDtoList) {
        log.info("getAgentDetailInfo start");
        Map<AgentInfoDto, List<Long>> agentChannelIdsMap = new HashMap<>();
        Map<Long, String> agentNameMap = new HashMap<>();
        Set<Long> allChannelIds = new HashSet<>();

        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            List<Long> ids = CommontUtil.parseIds(agentInfo.getChannelIds());
            agentChannelIdsMap.put(agentInfo, ids);
            agentNameMap.put(agentInfo.getAgentNo(),agentInfo.getAgentName());
            allChannelIds.addAll(ids);
        }

        Map<Long, ChannelDto> channelMap = allChannelIds.isEmpty()
                ? Collections.emptyMap()
                : channelMapper.getPaymentIdsByChannelIds(new ArrayList<>(allChannelIds), null).stream()
                .filter(p -> p != null && p.getChannelId() != null)
                .collect(Collectors.toMap(ChannelDto::getChannelId, v -> v, (a, b) -> a));
        log.info("getPaymentIdsByChannelIds channelMap size: {}", channelMap.size());
        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            List<Long> ids = agentChannelIdsMap.getOrDefault(agentInfo, Collections.emptyList());

            List<ChannelDto> list = agentInfo.getChannelDtoList();
            if (list == null) {
                list = new ArrayList<>();
                agentInfo.setChannelDtoList(list);
            }

            for (Long pid : ids) {
                ChannelDto p = channelMap.get(pid);
                if (p != null) {
                    list.add(p);
                }
            }

            String parentAgentName = agentNameMap.get(agentInfo.getAgentNo());
            if (parentAgentName != null) {
                agentInfo.setParentAgentName(parentAgentName);
            }
        }
        log.info("getAgentDetailInfo end");

    }


}
