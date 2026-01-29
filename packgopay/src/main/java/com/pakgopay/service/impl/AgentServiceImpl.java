package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.account.AccountInfoEntity;
import com.pakgopay.data.entity.agent.AgentInfoEntity;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.agent.AgentAddRequest;
import com.pakgopay.data.reqeust.agent.AgentEditRequest;
import com.pakgopay.data.reqeust.agent.AgentQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.account.WithdrawalAccountResponse;
import com.pakgopay.data.response.agent.AgentResponse;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.ChannelMapper;
import com.pakgopay.mapper.WithdrawalAccountsMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.ChannelDto;
import com.pakgopay.mapper.dto.WithdrawalAccountsDto;
import com.pakgopay.service.AgentService;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.ExportFileUtils;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.TransactionUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
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
    private WithdrawalAccountsMapper withdrawalAccountsMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Override
    public CommonResponse queryAgents(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        AgentResponse response = fetchAgentPage(agentQueryRequest);
        return CommonResponse.success(response);
    }

    private AgentResponse fetchAgentPage(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        AgentInfoEntity entity = new AgentInfoEntity();
        entity.setAgentName(agentQueryRequest.getAgentName());
        entity.setAccountName(agentQueryRequest.getAccountName());
        entity.setStatus(agentQueryRequest.getStatus());
        entity.setPageNo(agentQueryRequest.getPageNo());
        entity.setPageSize(agentQueryRequest.getPageSize());

        AgentResponse response = new AgentResponse();
        try {
            Integer totalNumber = agentInfoMapper.countByQuery(entity);
            List<AgentInfoDto> agentInfoDtoList = agentInfoMapper.pageByQuery(entity);
            enrichAgentDetails(agentInfoDtoList);

            response.setAgentInfoDtoList(agentInfoDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("agentInfoMapper fetchAgentPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    private void enrichAgentDetails(List<AgentInfoDto> agentInfoDtoList) {
        if (agentInfoDtoList == null || agentInfoDtoList.isEmpty()) {
            return;
        }

        // 1) Collect distinct parentIds
        List<String> parentIds = agentInfoDtoList.stream()
                .map(AgentInfoDto::getParentId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        // 2) Query parent agents in batch (avoid IN ())
        List<AgentInfoDto> parentAgentInfos = parentIds.isEmpty()
                ? Collections.emptyList()
                : agentInfoMapper.findByUserIds(parentIds);

        // 4) Parse channelIds once and collect all unique channelIds
        Map<String, AgentInfoDto> agentInfoMap = new HashMap<>();
        Set<Long> allChannelIds = new HashSet<>();
        Map<String, List<Long>> channelIdsByUserId = new HashMap<>();
        for (AgentInfoDto agentInfo : parentAgentInfos) {
            List<Long> ids = CommonUtil.parseIds(agentInfo.getChannelIds());
            agentInfoMap.put(agentInfo.getUserId(), agentInfo);
            channelIdsByUserId.put(agentInfo.getUserId(), ids);
            allChannelIds.addAll(ids);
        }

        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            List<Long> ids = CommonUtil.parseIds(agentInfo.getChannelIds());
            agentInfoMap.put(agentInfo.getUserId(), agentInfo);
            channelIdsByUserId.put(agentInfo.getUserId(), ids);
            allChannelIds.addAll(ids);
        }

        Map<Long, ChannelDto> channelMap = allChannelIds.isEmpty()
                ? Collections.emptyMap()
                : channelMapper.getPaymentIdsByChannelIds(new ArrayList<>(allChannelIds), null).stream()
                .filter(p -> p != null && p.getChannelId() != null)
                .collect(Collectors.toMap(ChannelDto::getChannelId, v -> v, (a, b) -> a));
        // 6) Fill detail info for each agent
        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            // agent's channel info
            List<Long> ids =
                    channelIdsByUserId.getOrDefault(agentInfo.getUserId(), new ArrayList<>());
            agentInfo.setChannelDtoList(buildChannelListByIds(ids,channelMap));
            agentInfo.setChannelIdList(ids);
            // parent agent's channel info
            if (agentInfo.getParentId() == null || agentInfo.getParentId().isEmpty()) {
                continue;
            }
            AgentInfoDto agentInfoDto = agentInfoMap.get(agentInfo.getParentId());
            if (agentInfoDto == null) {
                continue;
            }
            List<Long> parentChannelIds =
                    channelIdsByUserId.getOrDefault(agentInfoDto.getUserId(), new ArrayList<>());
            agentInfo.setParentChannelDtoList(buildChannelListByIds(parentChannelIds,channelMap));
            agentInfo.setParentAgentName(agentInfoDto.getAgentName());
            agentInfo.setParentUserName(agentInfoDto.getAccountName());
        }
    }

    /**
     * Build ChannelDto list by channelId list and channel map.
     * Keeps the order of ids and ignores non-existing ids.
     */
    public static List<ChannelDto> buildChannelListByIds(List<Long> ids, Map<Long, ChannelDto> channelMap) {
        if (ids == null || ids.isEmpty() || channelMap == null || channelMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChannelDto> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null) continue;
            ChannelDto c = channelMap.get(id);
            if (c != null) {
                list.add(c);
            }
        }
        return list;
    }

    @Override
    public void exportAgents(AgentQueryRequest agentQueryRequest, HttpServletResponse response) throws PakGoPayException, IOException {
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
                (req) -> fetchAgentPage(req).getAgentInfoDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);
    }

    @Override
    public CommonResponse updateAgent(AgentEditRequest agentEditRequest) throws PakGoPayException {
        AgentInfoDto agentInfoDto = buildAgentUpdateDto(agentEditRequest);
        try {
            int ret = agentInfoMapper.updateByAgentNo(agentInfoDto);
            log.info("editAgent updateByChannelId done, agentName={}, ret={}", agentEditRequest.getAgentName(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "agent not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editAgent updateByChannelId failed, agentName={}", agentEditRequest.getAgentName(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AgentInfoDto buildAgentUpdateDto(AgentEditRequest agentEditRequest) throws PakGoPayException {
        AgentInfoDto dto = new AgentInfoDto();
        dto.setAgentNo(PatchBuilderUtil.parseRequiredLong(agentEditRequest.getAgentNo(),"agentNo"));
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(agentEditRequest).to(dto)

                // basic
                .str(agentEditRequest::getAgentName, dto::setAgentName)
                .obj(agentEditRequest::getStatus, dto::setStatus)
                .ids(agentEditRequest::getChannelIds, dto::setChannelIds)

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

                // operator
                .str(agentEditRequest::getUserName, dto::setUpdateBy)

                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse createAgent(AgentAddRequest agentAddRequest) throws PakGoPayException {
        CreateUserRequest createUserRequest = buildAgentUserCreateRequest(agentAddRequest);
        AgentInfoDto agentInfoDto = buildAgentCreateDto(agentAddRequest);

        transactionUtil.runInTransaction(() -> {
            Long userId = userService.createUser(createUserRequest);

            agentInfoDto.setUserId(userId.toString());
            if(agentInfoDto.getLevel() == 1){
                agentInfoDto.setTopAgentId(userId.toString());
            }
            agentInfoMapper.insert(agentInfoDto);
        });

        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AgentInfoDto buildAgentCreateDto(AgentAddRequest req) throws PakGoPayException {
        AgentInfoDto dto = new AgentInfoDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<AgentAddRequest, AgentInfoDto> b = PatchBuilderUtil.from(req).to(dto)

                // =====================
                // 1) Identity & Account Info
                // =====================
                .reqStr("agentName", req::getAgentName, dto::setAgentName)
                .reqStr("accountName", req::getAccountName, dto::setAccountName)
                .ifTrue(req.getLevel() != 1)
                .reqStr("topAgentId",req::getTopAgentId, dto::setTopAgentId)
                .reqStr("parentId",req::getParentId, dto::setParentId)
                .endSkip()
                .reqObj("level", req::getLevel, dto::setLevel)
                .ids(req::getChannelIds, dto::setChannelIds)

                // =====================
                // 2) Status
                // =====================
                .reqObj("status", req::getStatus, dto::setStatus)

                // =====================
                // 4) Collection Configuration
                // =====================
                .reqObj("collectionRate", req::getCollectionRate, dto::setCollectionRate)
                .reqObj("collectionFixedFee", req::getCollectionFixedFee, dto::setCollectionFixedFee)
                .reqObj("collectionMaxFee", req::getCollectionMaxFee, dto::setCollectionMaxFee)
                .reqObj("collectionMinFee", req::getCollectionMinFee, dto::setCollectionMinFee)

                // =====================
                // 5) Payout Configuration
                // =====================
                .reqObj("payRate", req::getPayRate, dto::setPayRate)
                .reqObj("payFixedFee", req::getPayFixedFee, dto::setPayFixedFee)
                .reqObj("payMaxFee", req::getPayMaxFee, dto::setPayMaxFee)
                .reqObj("payMinFee", req::getPayMinFee, dto::setPayMinFee)

                // =====================
                // 7) Other (optional)
                // =====================
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        return b.build();
    }

    private CreateUserRequest buildAgentUserCreateRequest(AgentAddRequest agentAddRequest) {
        CreateUserRequest dto = new CreateUserRequest();
        long now = System.currentTimeMillis() / 1000;
        dto.setRoleId(CommonConstant.ROLE_AGENT);

        PatchBuilderUtil<AgentAddRequest, CreateUserRequest> builder = PatchBuilderUtil.from(agentAddRequest).to(dto)
                .str(agentAddRequest::getAccountName, dto::setLoginName)
                .str(agentAddRequest::getAccountPwd, dto::setPassword)
                .str(agentAddRequest::getAccountConfirmPwd, dto::setConfirmPassword)
                .str(agentAddRequest::getUserId, dto::setOperatorId)
                .reqStr("contactName", agentAddRequest::getContactName, dto::setContactName)
                .reqStr("contactEmail", agentAddRequest::getContactEmail, dto::setContactEmail)
                .reqStr("contactPhone", agentAddRequest::getContactPhone, dto::setContactPhone)
                .obj(agentAddRequest::getStatus, dto::setStatus)
                .str(agentAddRequest::getLoginIps, dto::setLoginIps)
                .str(agentAddRequest::getWithdrawalIps, dto::setWithdrawalIps);

        return builder.build();
    }

    @Override
    public CommonResponse queryAgentAccounts(AccountQueryRequest accountQueryRequest) {
        WithdrawalAccountResponse response = fetchAgentAccountPage(accountQueryRequest);
        return CommonResponse.success(response);
    }

    private WithdrawalAccountResponse fetchAgentAccountPage(
            AccountQueryRequest accountQueryRequest) throws PakGoPayException {
        AccountInfoEntity entity = new AccountInfoEntity();
        entity.setName(accountQueryRequest.getName());
        entity.setWalletAddr(accountQueryRequest.getWalletAddr());
        entity.setStartTime(accountQueryRequest.getStartTime());
        entity.setEndTime(accountQueryRequest.getEndTime());
        entity.setPageSize(accountQueryRequest.getPageSize());
        entity.setPageNo(accountQueryRequest.getPageNo());

        WithdrawalAccountResponse response = new WithdrawalAccountResponse();
        try {
            Integer totalNumber = withdrawalAccountsMapper.countByQueryAgent(entity);
            List<WithdrawalAccountsDto> withdrawalAccountsDtoList = withdrawalAccountsMapper.pageByQueryAgent(entity);

            if (accountQueryRequest.getIsNeedCardData()) {
                String userId = accountQueryRequest.getUserId();
                if (accountQueryRequest.getUserId() != null && !accountQueryRequest.getUserId().isEmpty()) {
                    List<String> userIds = new ArrayList<>();
                    userIds.add(userId);
                    Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(userIds);
                    response.setCardInfo(cardInfo);
                }
            }

            response.setWithdrawalAccountsDtoList(withdrawalAccountsDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("withdrawalAccountsMapper pageByQuery failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        return response;
    }

    @Override
    public void exportAgentAccounts(
            AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException {
        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<WithdrawalAccountsDto> colRes =
                ExportFileUtils.parseColumns(accountQueryRequest, ExportReportDataColumns.AGENT_ACCOUNT_ALLOWED);

        // 2) Init paging params
        accountQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        accountQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                accountQueryRequest,
                (req) -> fetchAgentAccountPage(req).getWithdrawalAccountsDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);
    }

    @Override
    public CommonResponse updateAgentAccount(AccountEditRequest accountEditRequest) {
        WithdrawalAccountsDto withdrawalAccountsDto = buildAgentAccountUpdateDto(accountEditRequest);
        try {
            int ret = withdrawalAccountsMapper.updateById(withdrawalAccountsDto);
            log.info("editAgentAccount updateByChannelId done, withdrawalId={}, ret={}", accountEditRequest.getId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "agent not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editAgentAccount updateByChannelId failed, withdrawalId={}", accountEditRequest.getId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private WithdrawalAccountsDto buildAgentAccountUpdateDto(AccountEditRequest accountEditRequest) {
        WithdrawalAccountsDto dto = new WithdrawalAccountsDto();
        dto.setId(PatchBuilderUtil.parseRequiredLong(accountEditRequest.getId(), "id"));
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(accountEditRequest).to(dto)
                .str(accountEditRequest::getWalletAddr, dto::setWalletAddr)
                .obj(accountEditRequest::getStatus, dto::setStatus)
                .str(accountEditRequest::getUserName, dto::setUpdateBy)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse createAgentAccount(AccountAddRequest accountAddRequest) {
        try {
            WithdrawalAccountsDto withdrawalAccountsDto = buildAgentAccountCreateDto(accountAddRequest);
            int ret = withdrawalAccountsMapper.insert(withdrawalAccountsDto);
            log.info("addAgentAccount insert done, ret={}", ret);
        } catch (PakGoPayException e) {
            log.error("addAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addAgentAccount failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("addAgentAccount insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private WithdrawalAccountsDto buildAgentAccountCreateDto(AccountAddRequest req) throws PakGoPayException {
        WithdrawalAccountsDto dto = new WithdrawalAccountsDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<AccountAddRequest, WithdrawalAccountsDto> b = PatchBuilderUtil.from(req).to(dto)
                .str(req::getWalletName, dto::setWalletName)
                .str(req::getWalletAddr, dto::setWalletAddr)
                .obj(req::getStatus, dto::setStatus)
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        dto.setMerchantAgentId(req.getMerchantAgentId());

        return b.build();
    }
}
