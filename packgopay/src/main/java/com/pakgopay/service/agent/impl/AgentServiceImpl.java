package com.pakgopay.service.agent.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.agent.AgentAddRequest;
import com.pakgopay.common.reqeust.agent.AgentEditRequest;
import com.pakgopay.common.reqeust.agent.AgentQueryRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.agent.AgentResponse;
import com.pakgopay.entity.agent.AgentInfoEntity;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.ChannelMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.service.agent.AgentService;
import com.pakgopay.service.login.impl.UserService;
import com.pakgopay.service.report.ExportReportDataColumns;
import com.pakgopay.util.CommontUtil;
import com.pakgopay.util.ExportFileUtils;
import com.pakgopay.util.PatchBuilderUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private UserService userService;

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

    @Override
    public void exportAgent(AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException {
        log.info("exportAgent start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<AgentInfoDto> colRes =
                ExportFileUtils.parseColumns(agentQueryRequest, ExportReportDataColumns.AGENT_ALLOWED);

        // 2) Init paging params
        agentQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        agentQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                agentQueryRequest,
                (req) -> queryAgentData(req).getAgentInfoDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);

        log.info("exportAgent end");
    }

    @Override
    public CommonResponse editAgent(AgentEditRequest agentEditRequest) throws PakGoPayException {
        log.info("editAgent start, agentName={}", agentEditRequest.getAgentName());

        AgentInfoDto agentInfoDto = checkAndGenerateAgentInfoDto(agentEditRequest);
        try {
            int ret = agentInfoMapper.updateByUserId(agentInfoDto);
            log.info("editAgent updateByChannelId done, agentName={}, ret={}", agentEditRequest.getAgentName(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "channel not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editAgent updateByChannelId failed, agentName={}", agentEditRequest.getAgentName(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editAgent end, agentName={}", agentEditRequest.getAgentName());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AgentInfoDto checkAndGenerateAgentInfoDto(AgentEditRequest agentEditRequest) throws PakGoPayException {
        AgentInfoDto dto = new AgentInfoDto();
        dto.setUserId(agentEditRequest.getUserId());
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(agentEditRequest).to(dto)

                // basic
                .str(agentEditRequest::getAgentName, dto::setAgentName)
                .obj(agentEditRequest::getStatus, dto::setStatus)
                .ids(agentEditRequest::getChannelIds, dto::setChannelIds)

                // contact
                .str(agentEditRequest::getContactName, dto::setContactName)
                .str(agentEditRequest::getContactEmail, dto::setContactEmail)
                .str(agentEditRequest::getContactPhone, dto::setContactPhone)

                // collection config
                .obj(agentEditRequest::getCollectionRate, dto::setCollectionRate)
                .obj(agentEditRequest::getCollectionFixedFee, dto::setCollectionFixedFee)
                .obj(agentEditRequest::getCollectionMaxFee, dto::setCollectionMaxFee)
                .obj(agentEditRequest::getCollectionMinFee, dto::setCollectionMinFee)

                // payout config
                .obj(agentEditRequest::getPayRate, dto::setPayRate)
                .obj(agentEditRequest::getPayFixedFee, dto::setPayFixedFee)
                .obj(agentEditRequest::getPayMaxFee, dto::setPayMaxFee)
                .obj(agentEditRequest::getPayMinFee, dto::setPayMinFee)

                // ip whitelist
                .str(agentEditRequest::getLoginIps, dto::setLoginIps)
                .str(agentEditRequest::getWithdrawIps, dto::setWithdrawIps)

                // operator
                .str(agentEditRequest::getUserName, dto::setUpdateBy)

                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse addAgent(AgentAddRequest agentAddRequest) throws PakGoPayException {
        log.info("addChannel start");

//        CreateUserRequest createUserRequest = generateUserCreateInfo(agentAddRequest);
//        try {
//            int ret = channelMapper.insert(channelDto);
//            log.info("addChannel insert done, ret={}", ret);
//        } catch (Exception e) {
//            log.error("addChannel insert failed", e);
//            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
//        }

        log.info("addChannel end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

}
