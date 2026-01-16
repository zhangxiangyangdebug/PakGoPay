package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.agent.AgentAccountInfoEntity;
import com.pakgopay.data.entity.agent.AgentInfoEntity;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.agent.*;
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
import com.pakgopay.util.CommontUtil;
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
        entity.setAccountName(agentQueryRequest.getAccountName());
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

        if (agentInfoDtoList == null || agentInfoDtoList.isEmpty()) {
            log.info("agentInfoDtoList is empty");
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
            List<Long> ids = CommontUtil.parseIds(agentInfo.getChannelIds());
            agentInfoMap.put(agentInfo.getUserId(), agentInfo);
            channelIdsByUserId.put(agentInfo.getUserId(), ids);
            allChannelIds.addAll(ids);
        }

        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            List<Long> ids = CommontUtil.parseIds(agentInfo.getChannelIds());
            agentInfoMap.put(agentInfo.getUserId(), agentInfo);
            channelIdsByUserId.put(agentInfo.getUserId(), ids);
            allChannelIds.addAll(ids);
        }

        Map<Long, ChannelDto> channelMap = allChannelIds.isEmpty()
                ? Collections.emptyMap()
                : channelMapper.getPaymentIdsByChannelIds(new ArrayList<>(allChannelIds), null).stream()
                .filter(p -> p != null && p.getChannelId() != null)
                .collect(Collectors.toMap(ChannelDto::getChannelId, v -> v, (a, b) -> a));
        log.info("getPaymentIdsByChannelIds channelMap size: {}", channelMap.size());
        // 6) Fill detail info for each agent
        for (AgentInfoDto agentInfo : agentInfoDtoList) {
            // agent's channel info
            List<Long> ids =
                    channelIdsByUserId.getOrDefault(agentInfo.getUserId(), new ArrayList<>());
            agentInfo.setChannelDtoList(buildChannelListByIds(ids,channelMap));
            // parent agent's channel info
            if (agentInfo.getParentId() == null || agentInfo.getParentId().isEmpty()) {
                continue;
            }
            AgentInfoDto agentInfoDto = agentInfoMap.get(agentInfo.getParentId());
            if (agentInfoDto == null) {
                continue;
            }
            List<Long> parentChannelIds =
                    channelIdsByUserId.getOrDefault(agentInfoDto.getParentId(), new ArrayList<>());
            agentInfo.setChannelDtoList(buildChannelListByIds(parentChannelIds,channelMap));
            agentInfo.setParentAgentName(agentInfoDto.getAgentName());
            agentInfo.setParentUserName(agentInfoDto.getParentUserName());
        }
        log.info("getAgentDetailInfo end");
    }

    /**
     * Build ChannelDto list by channelId list and channel map.
     * Keeps the order of ids and ignores non-existing ids.
     */
    private static List<ChannelDto> buildChannelListByIds(List<Long> ids, Map<Long, ChannelDto> channelMap) {
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

        CreateUserRequest createUserRequest = generateUserCreateInfo(agentAddRequest);
        AgentInfoDto agentInfoDto = generateAgentInfoDtoForAdd(agentAddRequest);

        transactionUtil.runInTransaction(() -> {
            Long userId = userService.createUser(createUserRequest);

            agentInfoDto.setUserId(userId.toString());
            if(agentInfoDto.getLevel() == 1){
                agentInfoDto.setTopAgentId(userId.toString());
            }
            agentInfoMapper.insert(agentInfoDto);
        });

        log.info("addChannel end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AgentInfoDto generateAgentInfoDtoForAdd(AgentAddRequest req) throws PakGoPayException {
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
                // 3) Contact Info
                // =====================
                .reqStr("contactName", req::getContactName, dto::setContactName)
                .reqStr("contactEmail", req::getContactEmail, dto::setContactEmail)
                .reqStr("contactPhone", req::getContactPhone, dto::setContactPhone)

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
                // 6) Security / Whitelist (optional)
                // =====================
                .str(req::getLoginIps, dto::setLoginIps)
                .str(req::getWithdrawIps, dto::setWithdrawIps)

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

    private CreateUserRequest generateUserCreateInfo(AgentAddRequest agentAddRequest) {
        CreateUserRequest dto = new CreateUserRequest();
        long now = System.currentTimeMillis() / 1000;
        dto.setRoleId(CommonConstant.ROLE_AGENT);

        PatchBuilderUtil<AgentAddRequest, CreateUserRequest> builder = PatchBuilderUtil.from(agentAddRequest).to(dto)
                .str(agentAddRequest::getAccountName, dto::setLoginName)
                .str(agentAddRequest::getAccountPwd, dto::setPassword)
                .str(agentAddRequest::getAccountConfirmPwd, dto::setConfirmPassword)
                .str(agentAddRequest::getUserId, dto::setOperatorId)
                .obj(agentAddRequest::getStatus, dto::setStatus);

        return builder.build();
    }

    @Override
    public CommonResponse queryAgentAccount(AccountQueryRequest accountQueryRequest) {
        log.info("queryAgentAccount start");
        WithdrawalAccountResponse response = queryAgentAccountData(accountQueryRequest);
        log.info("queryAgentAccount end");
        return CommonResponse.success(response);
    }

    private WithdrawalAccountResponse queryAgentAccountData(
            AccountQueryRequest accountQueryRequest) throws PakGoPayException {
        log.info("queryAgentAccountData start");
        AgentAccountInfoEntity entity = new AgentAccountInfoEntity();
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
                List<String> userIds = withdrawalAccountsMapper.userIdsByQueryAgent(entity);
                if (userIds != null && userIds.isEmpty()) {
                    Map<String, Map<String, BigDecimal>> cardInfo = balanceService.getBalanceInfos(userIds);
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
        log.info("queryAgentAccountData end");
        return response;
    }

    @Override
    public void exportAgentAccount(
            AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException {
        log.info("exportAgentAccount start");

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
                (req) -> queryAgentAccountData(req).getWithdrawalAccountsDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);

        log.info("exportAgentAccount end");
    }

    @Override
    public CommonResponse editAgentAccount(AccountEditRequest accountEditRequest) {
        log.info("editAgentAccount start, withdrawalId={}", accountEditRequest.getId());

        WithdrawalAccountsDto withdrawalAccountsDto = generateAccountsDto(accountEditRequest);
        try {
            int ret = withdrawalAccountsMapper.updateById(withdrawalAccountsDto);
            log.info("editAgentAccount updateByChannelId done, withdrawalId={}, ret={}", accountEditRequest.getId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "channel not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editAgentAccount updateByChannelId failed, withdrawalId={}", accountEditRequest.getId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editAgentAccount end, withdrawalId={}", accountEditRequest.getId());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private WithdrawalAccountsDto generateAccountsDto(AccountEditRequest accountEditRequest) {
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
    public CommonResponse addAgentAccount(AccountAddRequest accountAddRequest) {
        log.info("addAgentAccount start");

        try {
            WithdrawalAccountsDto withdrawalAccountsDto = generateAccountInfoDtoForAdd(accountAddRequest);
            int ret = withdrawalAccountsMapper.insert(withdrawalAccountsDto);
            log.info("addAgentAccount insert done, ret={}", ret);
        } catch (PakGoPayException e) {
            log.error("addAgentAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addAgentAccount failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("addAgentAccount insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("addAgentAccount end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private WithdrawalAccountsDto generateAccountInfoDtoForAdd(AccountAddRequest req) throws PakGoPayException {
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

        AgentInfoDto agentInfoDto = agentInfoMapper.findByAgentName(req.getName())
                .orElseThrow(() -> new PakGoPayException(
                        ResultCode.USER_IS_NOT_EXIST
                        , "agent is not exists, agentName:" + req.getName()));
        dto.setMerchantAgentId(agentInfoDto.getUserId());

        return b.build();
    }
}
