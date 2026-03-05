package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
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
import com.pakgopay.data.response.BalanceUserInfo;
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
import com.pakgopay.service.common.CommonService;
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

    @Autowired
    private CommonService commonService;

    @Override
    public CommonResponse queryAgents(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        AgentResponse response = fetchAgentPage(agentQueryRequest);
        return CommonResponse.success(response);
    }

    /**
     * Agent query rules:
     * 1) No filter params: query all visible agents.
     * 2) isSearchFirstLevel=true: query first-level agents (level=1) in visible scope.
     * 3) isSearchNextLevel=true: query direct children of target agent.
     *    target resolution:
     *    - if agentName/accountName provided, use matched target;
     *    - if not provided and operator is agent, default target=operator self;
     *    - if not provided and operator is admin, reject as invalid params.
     *
     * Permission rules:
     * - admin can query all agents.
     * - agent can query self and all descendants only.
     */
    private AgentResponse fetchAgentPage(AgentQueryRequest agentQueryRequest) throws PakGoPayException {
        log.info("fetchAgentPage start, operatorUserId={}, agentName={}, accountName={}, isSearchFirstLevel={}, isSearchNextLevel={}, status={}",
                agentQueryRequest.getUserId(),
                agentQueryRequest.getAgentName(),
                agentQueryRequest.getAccountName(),
                agentQueryRequest.getIsSearchFirstLevel(),
                agentQueryRequest.getIsSearchNextLevel(),
                agentQueryRequest.getStatus());
        AgentInfoEntity entity = new AgentInfoEntity();

        Integer roleId = commonService.getRoleIdByUserId(agentQueryRequest.getUserId());
        boolean isAgent = Objects.equals(roleId, CommonConstant.ROLE_AGENT);
        log.info("fetchAgentPage role resolved, operatorUserId={}, roleId={}, isAgent={}",
                agentQueryRequest.getUserId(), roleId, isAgent);
        if (!Objects.equals(roleId, CommonConstant.ROLE_ADMIN) && !isAgent) {
            log.warn("fetchAgentPage forbidden, operatorUserId={}, roleId={}",
                    agentQueryRequest.getUserId(), roleId);
            throw new PakGoPayException(ResultCode.USER_HAS_NO_ROLE_PERMISSION);
        }

        Set<String> visibleUserIds = null;
        if (isAgent) {
            visibleUserIds = collectSelfAndDescendantUserIds(agentQueryRequest.getUserId());
            if (visibleUserIds.isEmpty()) {
                log.info("fetchAgentPage agent scope empty, operatorUserId={}", agentQueryRequest.getUserId());
                return buildEmptyAgentResponse(agentQueryRequest);
            }
            entity.setAllowedUserIds(new ArrayList<>(visibleUserIds));
            log.info("fetchAgentPage agent scope loaded, operatorUserId={}, visibleSize={}",
                    agentQueryRequest.getUserId(), visibleUserIds.size());
        }

        String agentName = agentQueryRequest.getAgentName();
        String accountName = agentQueryRequest.getAccountName();
        boolean hasAgentName = agentName != null && !agentName.isBlank();
        boolean hasAccountName = accountName != null && !accountName.isBlank();
        if (Boolean.TRUE.equals(agentQueryRequest.getIsSearchNextLevel())) {
            AgentInfoDto targetAgent = resolveNextLevelTarget(
                    agentQueryRequest.getUserId(),
                    isAgent,
                    hasAgentName,
                    hasAccountName,
                    agentName,
                    accountName);
            if (targetAgent == null) {
                log.info("fetchAgentPage next-level target not found, operatorUserId={}, agentName={}, accountName={}",
                        agentQueryRequest.getUserId(), agentName, accountName);
                return buildEmptyAgentResponse(agentQueryRequest);
            }
            log.info("fetchAgentPage next-level target resolved, operatorUserId={}, targetUserId={}",
                    agentQueryRequest.getUserId(), targetAgent.getUserId());
            if (visibleUserIds != null && !visibleUserIds.contains(targetAgent.getUserId())) {
                log.warn("fetchAgentPage next-level target forbidden, operatorUserId={}, targetUserId={}",
                        agentQueryRequest.getUserId(), targetAgent.getUserId());
                return buildEmptyAgentResponse(agentQueryRequest);
            }
            entity.setParentId(targetAgent.getUserId());
        } else {
            // Normal query path: exact filters on agent/account name.
            if (hasAgentName) {
                entity.setAgentName(agentName);
            }
            if (hasAccountName) {
                entity.setAccountName(accountName);
            }
            if (Boolean.TRUE.equals(agentQueryRequest.getIsSearchFirstLevel())) {
                entity.setLevel(1);
                log.info("fetchAgentPage first-level filter enabled, operatorUserId={}", agentQueryRequest.getUserId());
            }
        }

        entity.setStatus(agentQueryRequest.getStatus());
        entity.setPageNo(agentQueryRequest.getPageNo());
        entity.setPageSize(agentQueryRequest.getPageSize());
        log.info("fetchAgentPage query condition: {}", JSON.toJSONString(entity));

        AgentResponse response = new AgentResponse();
        try {
            Integer totalNumber = agentInfoMapper.countByQuery(entity);
            List<AgentInfoDto> agentInfoDtoList = agentInfoMapper.pageByQuery(entity);
            enrichAgentDetails(agentInfoDtoList);

            response.setAgentInfoDtoList(agentInfoDtoList);
            response.setTotalNumber(totalNumber);
            log.info("fetchAgentPage db query done, totalNumber={}, pageSize={}",
                    totalNumber, agentInfoDtoList == null ? 0 : agentInfoDtoList.size());
        } catch (Exception e) {
            log.error("agentInfoMapper fetchAgentPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("fetchAgentPage end, pageNo={}, pageSize={}, totalNumber={}",
                response.getPageNo(), response.getPageSize(), response.getTotalNumber());
        return response;
    }

    private AgentResponse buildEmptyAgentResponse(AgentQueryRequest request) {
        log.info("buildEmptyAgentResponse, operatorUserId={}, pageNo={}, pageSize={}",
                request.getUserId(), request.getPageNo(), request.getPageSize());
        AgentResponse response = new AgentResponse();
        response.setAgentInfoDtoList(Collections.emptyList());
        response.setTotalNumber(0);
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        return response;
    }

    private AgentInfoDto resolveNextLevelTarget(
            String operatorUserId,
            boolean isAgent,
            boolean hasAgentName,
            boolean hasAccountName,
            String agentName,
            String accountName) {
        if (hasAgentName || hasAccountName) {
            AgentInfoDto target = agentInfoMapper.findByAgentNameOrAccountName(agentName, accountName).orElse(null);
            log.info("resolveNextLevelTarget by filter, operatorUserId={}, agentName={}, accountName={}, found={}",
                    operatorUserId, agentName, accountName, target != null);
            return target;
        }
        if (isAgent) {
            AgentInfoDto target = agentInfoMapper.findByUserId(operatorUserId);
            log.info("resolveNextLevelTarget by self, operatorUserId={}, found={}",
                    operatorUserId, target != null);
            return target;
        }
        log.warn("resolveNextLevelTarget invalid params, operatorUserId={}, agentName={}, accountName={}",
                operatorUserId, agentName, accountName);
        throw new PakGoPayException(ResultCode.INVALID_PARAMS, "agentName/accountName is required");
    }

    private Set<String> collectSelfAndDescendantUserIds(String rootUserId) {
        if (rootUserId == null || rootUserId.isBlank()) {
            log.warn("collectSelfAndDescendantUserIds skipped: rootUserId is empty");
            return Collections.emptySet();
        }
        List<String> descendants = agentInfoMapper.findDescendantUserIdsByAncestor(rootUserId);
        Set<String> result = descendants == null ? Collections.emptySet() : new LinkedHashSet<>(descendants);
        log.info("collectSelfAndDescendantUserIds done, rootUserId={}, size={}", rootUserId, result.size());
        return result;
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

        List<String> userIds = agentInfoDtoList.stream().map(AgentInfoDto::getUserId).collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            BalanceUserInfo balanceUserInfo = balanceService.fetchBalanceSummaries(userIds);
            Map<String, Map<String, Map<String, BigDecimal>>> userDataMap = balanceUserInfo.getUserDataMap();
            agentInfoDtoList.forEach(info -> {
                info.setBalanceInfo(userDataMap.get(info.getUserId()));
            });
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
        validateAgentFeeAboveParent(agentEditRequest.getParentId(), agentEditRequest);
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
        validateAgentFeeAboveParent(agentAddRequest.getParentId(), agentAddRequest);
        AgentInfoDto agentInfoDto = buildAgentCreateDto(agentAddRequest);

        transactionUtil.runInTransaction(() -> {
            if (agentInfoMapper.findByAgentName(agentAddRequest.getAgentName()).isPresent()) {
                throw new PakGoPayException(ResultCode.FAIL, "agent name already exists");
            }
            Long userId = userService.createUser(createUserRequest);

            agentInfoDto.setUserId(userId.toString());
            if(agentInfoDto.getLevel() == 1){
                agentInfoDto.setTopAgentId(userId.toString());
            }
            agentInfoMapper.insert(agentInfoDto);
        });

        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private void validateAgentFeeAboveParent(String parentId, Object request) throws PakGoPayException {
        if (parentId == null || parentId.isBlank()) {
            return;
        }
        AgentInfoDto parent = agentInfoMapper.findByUserId(parentId);
        if (parent == null) {
            return;
        }

        BigDecimal agentColRate = null;
        BigDecimal agentColFixed = null;
        BigDecimal agentPayRate = null;
        BigDecimal agentPayFixed = null;
        if (request instanceof AgentAddRequest addReq) {
            agentColRate = addReq.getCollectionRate();
            agentColFixed = addReq.getCollectionFixedFee();
            agentPayRate = addReq.getPayRate();
            agentPayFixed = addReq.getPayFixedFee();
        } else if (request instanceof AgentEditRequest editReq) {
            agentColRate = editReq.getCollectionRate();
            agentColFixed = editReq.getCollectionFixedFee();
            agentPayRate = editReq.getPayRate();
            agentPayFixed = editReq.getPayFixedFee();
        }

        if (agentColRate != null && parent.getCollectionRate() != null
                && agentColRate.compareTo(parent.getCollectionRate()) < 0) {
            log.error("agent collection rate must > parent rate, agentColRate={}, parentColRate={}",
                    agentColRate, parent.getCollectionRate());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "agent collection rate must > parent rate");
        }
        if (agentColFixed != null && parent.getCollectionFixedFee() != null
                && agentColFixed.compareTo(parent.getCollectionFixedFee()) < 0) {
            log.error("agent collection fixed fee must > parent fixed fee, agentColFixed={}, parentColFixed={}",
                    agentColFixed, parent.getCollectionFixedFee());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "agent collection fixed fee must > parent fixed fee");
        }
        if (agentPayRate != null && parent.getPayRate() != null
                && agentPayRate.compareTo(parent.getPayRate()) < 0) {
            log.error("agent pay rate must > parent rate, agentPayRate={}, parentPayRate={}",
                    agentPayRate, parent.getPayRate());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "agent pay rate must > parent rate");
        }
        if (agentPayFixed != null && parent.getPayFixedFee() != null
                && agentPayFixed.compareTo(parent.getPayFixedFee()) < 0) {
            log.error("agent pay fixed fee must > parent fixed fee, agentPayFixed={}, parentPayFixed={}",
                    agentPayFixed, parent.getPayFixedFee());
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "agent pay fixed fee must > parent fixed fee");
        }
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
                    Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(userIds).getTotalData();
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
