package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.account.AccountInfoEntity;
import com.pakgopay.data.entity.merchant.MerchantEntity;
import com.pakgopay.data.reqeust.CreateUserRequest;
import com.pakgopay.data.reqeust.account.AccountAddRequest;
import com.pakgopay.data.reqeust.account.AccountEditRequest;
import com.pakgopay.data.reqeust.account.AccountQueryRequest;
import com.pakgopay.data.reqeust.merchant.MerchantAddRequest;
import com.pakgopay.data.reqeust.merchant.MerchantEditRequest;
import com.pakgopay.data.reqeust.merchant.MerchantQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.account.WithdrawalAccountResponse;
import com.pakgopay.data.response.merchant.MerchantResponse;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.MerchantService;
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

    @Autowired
    private WithdrawalAccountsMapper withdrawalAccountsMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;


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

            if (merchantQueryRequest.getIsNeedCardData()) {
                List<String> userIds = merchantInfoMapper.userIdsByQueryMerchant(entity);
                if (userIds != null && !userIds.isEmpty()) {
                    Map<String, Map<String, BigDecimal>> cardInfo = balanceService.getBalanceInfos(userIds);
                    response.setCardInfo(cardInfo);
                }
            }

            response.setMerchantInfoDtoList(merchantInfoDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("merchantInfoMapper queryAgentData failed, message {}", e.getMessage());
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
            List<Long> channelIds;
            if (merchant.getParentId() != null && !merchant.getParentId().isBlank()) {
                // Merchant has an agent, use agent's channelIds
                AgentInfoDto parentAgent = agentByUserIdMap.get(merchant.getParentId());
                channelIds = CommontUtil.parseIds(parentAgent == null ? null : parentAgent.getChannelIds());
                List<AgentInfoDto> agentChain = buildAgentChain(agentByUserIdMap, merchant.getParentId());
                merchant.setAgentInfos(agentChain);
            } else {
                // No agent, use merchant's own channelIds
                channelIds = CommontUtil.parseIds(merchant.getChannelIds());
                List<ChannelDto> channelInfos = buildAgentChain(channelByIdMap, channelIds);
                merchant.setChannelDtoList(channelInfos);
            }

            List<String> currencies = resolveCurrenciesByChannelIds(channelIds, channelByIdMap, paymentByIdMap);
            merchant.setCurrencyList(currencies);
        }
    }

    /**
     * Build agent chain list: [parentAgent, upperAgent]
     * Upper agent is defined as parentAgent.parentId (one level up).
     */
    private static List<ChannelDto> buildAgentChain(Map<Long, ChannelDto> channelByIdMap, List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChannelDto> result = new ArrayList<>();
        for (Long channelId : channelIds) {
            ChannelDto dto = channelByIdMap.get(channelId);
            if (dto == null) continue;
            result.add(dto);
        }
        return result;
    }

    /**
     * Build agent chain list: [parentAgent, upperAgent]
     * Upper agent is defined as parentAgent.parentId (one level up).
     */
    private static List<AgentInfoDto> buildAgentChain(Map<String, AgentInfoDto> agentByUserId, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return Collections.emptyList();
        }

        List<AgentInfoDto> chainBottomToTop = new ArrayList<>(3);

        String curId = parentId;
        int guard = 0; // prevent accidental cycles
        while (curId != null && !curId.isBlank() && guard++ < 10) {
            AgentInfoDto cur = agentByUserId.get(curId);
            if (cur == null) {
                break;
            }

            chainBottomToTop.add(cur);

            // Move upward: current's parentId
            curId = cur.getParentId();
        }

        if (chainBottomToTop.isEmpty()) {
            return Collections.emptyList();
        }

        // Reverse to [top -> ... -> bottom]
        Collections.reverse(chainBottomToTop);
        return chainBottomToTop;
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

    @Override
    public CommonResponse editMerchant(MerchantEditRequest merchantEditRequest) {
        log.info("editMerchant start, merchantUserId={}", merchantEditRequest.getMerchantUserId());

        MerchantInfoDto merchantInfoDto = checkAndGenerateMerchantInfo(merchantEditRequest);
        try {
            int ret = merchantInfoMapper.updateByUserId(merchantInfoDto);
            log.info("editMerchant updateByChannelId done, merchantUserId={}, ret={}", merchantEditRequest.getMerchantUserId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "merchant not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editMerchant updateByChannelId failed, merchantUserId={}", merchantEditRequest.getMerchantUserId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editMerchant end, merchantUserId={}", merchantEditRequest.getMerchantUserId());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private MerchantInfoDto checkAndGenerateMerchantInfo(
            MerchantEditRequest req) throws PakGoPayException {
        MerchantInfoDto dto = new MerchantInfoDto();
        dto.setUserId(req.getMerchantUserId());
        dto.setUpdateTime(System.currentTimeMillis() / 1000);

        return PatchBuilderUtil.from(req).to(dto)
                // basic
                .str(req::getParentId, dto::setParentId)
                .str(req::getMerchantName, dto::setMerchantName)
                .obj(req::getSupportType, dto::setSupportType)
                .obj(req::getStatus, dto::setStatus)
                .obj(req::getRiskLevel, dto::setRiskLevel)
                .obj(req::getNotificationEnable, dto::setNotificationEnable)

                // collection fee config
                .obj(req::getCollectionRate, dto::setCollectionRate)
                .obj(req::getCollectionFixedFee, dto::setCollectionFixedFee)
                .obj(req::getCollectionMaxFee, dto::setCollectionMaxFee)
                .obj(req::getCollectionMinFee, dto::setCollectionMinFee)

                // payout fee config
                .obj(req::getPayRate, dto::setPayRate)
                .obj(req::getPayFixedFee, dto::setPayFixedFee)
                .obj(req::getPayMaxFee, dto::setPayMaxFee)
                .obj(req::getPayMinFee, dto::setPayMinFee)

                // float & whitelist
                .obj(req::getIsFloat, dto::setIsFloat)
                .obj(req::getFloatRange, dto::setFloatRange)
                .str(req::getColWhiteIps, dto::setColWhiteIps)
                .str(req::getPayWhiteIps, dto::setPayWhiteIps)

                // channel ids (List<Long> -> "1,2,3")
                .ids(req::getChannelIds, dto::setChannelIds)
                .str(req::getUserName, dto::setUpdateBy)
                .throwIfNoUpdate(new PakGoPayException(ResultCode.INVALID_PARAMS, "no data need to update"));
    }

    @Override
    public CommonResponse addMerchant(MerchantAddRequest merchantAddRequest) {
        log.info("addChannel start");

        CreateUserRequest createUserRequest = generateUserCreateInfo(merchantAddRequest);
        MerchantInfoDto merchantInfoDto = generateMerchantInfoForAdd(merchantAddRequest);

        transactionUtil.runInTransaction(() -> {
            Long userId = userService.createUser(createUserRequest);

            merchantInfoDto.setUserId(userId.toString());
            merchantInfoMapper.insert(merchantInfoDto);
        });

        log.info("addChannel end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private MerchantInfoDto generateMerchantInfoForAdd(MerchantAddRequest req) {
        MerchantInfoDto dto = new MerchantInfoDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<MerchantAddRequest, MerchantInfoDto> b = PatchBuilderUtil.from(req).to(dto)
                // =====================
                // 1) Identity & Ownership
                // =====================
                .str(req::getParentId, dto::setParentId)
                .reqStr("merchantName", req::getMerchantName, dto::setMerchantName)

                // =====================
                // 2) Basic Status & Capability
                // =====================
                .reqObj("supportType", req::getSupportType, dto::setSupportType)
                .reqObj("status", req::getStatus, dto::setStatus)

                // =====================
                // 3) Risk & Notification
                // =====================
                .obj(req::getRiskLevel, dto::setRiskLevel)
                .obj(req::getNotificationEnable, dto::setNotificationEnable)

                // =====================
                // 4) Collection Fee Configuration
                // =====================
                .ifTrue(CommonConstant.SUPPORT_TYPE_COLLECTION.equals(req.getSupportType())
                        || CommonConstant.SUPPORT_TYPE_ALL.equals(req.getSupportType()))
                .reqObj("collectionRate", req::getCollectionRate, dto::setCollectionRate)
                .reqObj("collectionFixedFee", req::getCollectionFixedFee, dto::setCollectionFixedFee)
                .reqObj("collectionMaxFee", req::getCollectionMaxFee, dto::setCollectionMaxFee)
                .reqObj("collectionMinFee", req::getCollectionMinFee, dto::setCollectionMinFee)
                .reqStr("colWhiteIps", req::getColWhiteIps, dto::setColWhiteIps)
                .endSkip()

                // =====================
                // 5) Payout Fee Configuration
                // =====================
                .ifTrue(CommonConstant.SUPPORT_TYPE_PAY.equals(req.getSupportType())
                        || CommonConstant.SUPPORT_TYPE_ALL.equals(req.getSupportType()))
                .reqObj("payRate", req::getPayRate, dto::setPayRate)
                .reqObj("payFixedFee", req::getPayFixedFee, dto::setPayFixedFee)
                .reqObj("payMaxFee", req::getPayMaxFee, dto::setPayMaxFee)
                .reqObj("payMinFee", req::getPayMinFee, dto::setPayMinFee)
                .reqStr("payWhiteIps", req::getPayWhiteIps, dto::setPayWhiteIps)
                .endSkip()

                // =====================
                // 6) Floating & Security
                // =====================
                .reqObj("isFloat", req::getIsFloat, dto::setIsFloat)
                .ifTrue(CommonConstant.ENABLE_STATUS_ENABLE.equals(req.getIsFloat()))
                .reqObj("floatRange", req::getFloatRange, dto::setFloatRange)
                .endSkip()

                // =====================
                // 7) Channel Configuration
                // =====================
                .str(req::getChannelIds, dto::setChannelIds);

        // 4) meta
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        return b.build();
    }

    private CreateUserRequest generateUserCreateInfo(MerchantAddRequest merchantAddRequest) {
        CreateUserRequest dto = new CreateUserRequest();
        long now = System.currentTimeMillis() / 1000;
        dto.setRoleId(CommonConstant.ROLE_MERCHANT);

        PatchBuilderUtil<MerchantAddRequest, CreateUserRequest> builder = PatchBuilderUtil.from(merchantAddRequest).to(dto)
                .str(merchantAddRequest::getAccountName, dto::setLoginName)
                .str(merchantAddRequest::getAccountPwd, dto::setPassword)
                .str(merchantAddRequest::getAccountConfirmPwd, dto::setConfirmPassword)
                .str(merchantAddRequest::getContactName, dto::setContactName)
                .str(merchantAddRequest::getContactEmail, dto::setContactEmail)
                .str(merchantAddRequest::getContactPhone, dto::setContactPhone)
                .str(merchantAddRequest::getUserId, dto::setOperatorId)
                .obj(merchantAddRequest::getStatus, dto::setStatus)
                .str(merchantAddRequest::getLoginIps, dto::setLoginIps);

        return builder.build();
    }

    @Override
    public CommonResponse queryMerchantAccount(AccountQueryRequest accountQueryRequest) {
        log.info("queryMerchantAccount start");
        WithdrawalAccountResponse response = queryMerchantAccountData(accountQueryRequest);
        log.info("queryMerchantAccount end");
        return CommonResponse.success(response);
    }

    private WithdrawalAccountResponse queryMerchantAccountData(AccountQueryRequest accountQueryRequest) {
        log.info("queryMerchantAccountData start");
        AccountInfoEntity entity = new AccountInfoEntity();
        entity.setName(accountQueryRequest.getName());
        entity.setWalletAddr(accountQueryRequest.getWalletAddr());
        entity.setStartTime(accountQueryRequest.getStartTime());
        entity.setEndTime(accountQueryRequest.getEndTime());
        entity.setPageSize(accountQueryRequest.getPageSize());
        entity.setPageNo(accountQueryRequest.getPageNo());

        WithdrawalAccountResponse response = new WithdrawalAccountResponse();
        try {
            Integer totalNumber = withdrawalAccountsMapper.countByQueryMerchant(entity);
            List<WithdrawalAccountsDto> withdrawalAccountsDtoList = withdrawalAccountsMapper.pageByQueryMerchant(entity);

            if (accountQueryRequest.getIsNeedCardData()) {
                List<String> userIds = withdrawalAccountsMapper.userIdsByQueryMerchant(entity);
                if (userIds != null && !userIds.isEmpty()) {
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
        log.info("queryMerchantAccountData end");
        return response;
    }

    @Override
    public void exportMerchantAccount(
            AccountQueryRequest accountQueryRequest, HttpServletResponse response) throws IOException {
        log.info("exportMerchantAccount start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<WithdrawalAccountsDto> colRes =
                ExportFileUtils.parseColumns(accountQueryRequest, ExportReportDataColumns.MERCHANT_ACCOUNT_ALLOWED);

        // 2) Init paging params
        accountQueryRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        accountQueryRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                accountQueryRequest,
                (req) -> queryMerchantAccountData(req).getWithdrawalAccountsDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_EXPORT_FILE_NAME);

        log.info("exportMerchantAccount end");
    }

    @Override
    public CommonResponse editMerchantAccount(AccountEditRequest accountEditRequest) {
        log.info("editMerchantAccount start, withdrawalId={}", accountEditRequest.getId());

        WithdrawalAccountsDto withdrawalAccountsDto = generateAccountsDto(accountEditRequest);
        try {
            int ret = withdrawalAccountsMapper.updateById(withdrawalAccountsDto);
            log.info("editMerchantAccount updateByChannelId done, withdrawalId={}, ret={}", accountEditRequest.getId(), ret);

            if (ret <= 0) {
                return CommonResponse.fail(ResultCode.FAIL, "merchant not found or no rows updated");
            }
        } catch (Exception e) {
            log.error("editMerchantAccount updateByChannelId failed, withdrawalId={}", accountEditRequest.getId(), e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("editMerchantAccount end, withdrawalId={}", accountEditRequest.getId());
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
    public CommonResponse addMerchantAccount(AccountAddRequest accountAddRequest) {
        log.info("addMerchantAccount start");

        try {
            WithdrawalAccountsDto withdrawalAccountsDto = generateAccountInfoDtoForAdd(accountAddRequest);
            int ret = withdrawalAccountsMapper.insert(withdrawalAccountsDto);
            log.info("addMerchantAccount insert done, ret={}", ret);
        } catch (PakGoPayException e) {
            log.error("addMerchantAccount failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            return CommonResponse.fail(e.getCode(), "addMerchantAccount failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("addMerchantAccount insert failed", e);
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("addMerchantAccount end");
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

        dto.setMerchantAgentId(req.getMerchantAgentId());

        return b.build();
    }
}
