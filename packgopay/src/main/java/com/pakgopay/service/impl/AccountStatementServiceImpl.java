package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.constant.NotificationComponentType;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.Message;
import com.pakgopay.data.entity.account.AccountStatementEntity;
import com.pakgopay.data.entity.account.AdjustmentStatementRecord;
import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementEditRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.account.AccountStatementsResponse;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.service.common.OrderInterventionTelegramNotifier;
import com.pakgopay.service.common.SendDmqMessage;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.SnowflakeIdService;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.pakgopay.util.CalcUtil;

@Slf4j
@Service
public class AccountStatementServiceImpl implements AccountStatementService {

    @Autowired
    private AccountStatementsMapper accountStatementsMapper;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private SnowflakeIdService snowflakeIdService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SendDmqMessage sendDmqMessage;

    @Autowired
    private OrderInterventionTelegramNotifier orderInterventionTelegramNotifier;

    @Override
    public CommonResponse queryAccountStatement(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("queryAccountStatement start");
        AccountStatementsResponse response = queryMerchantRechargeData(
                accountStatementQueryRequest);
        log.info("query AccountStatement end");
        return CommonResponse.success(response);
    }

    private AccountStatementsResponse queryMerchantRechargeData(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("queryMerchantRechargeData start");
        AccountStatementEntity entity = new AccountStatementEntity();
        entity.setId(accountStatementQueryRequest.getId());
        entity.setUserId(accountStatementQueryRequest.getMerchantAgentId());
        entity.setOrderType(accountStatementQueryRequest.getOrderType());
        entity.setUserRole(accountStatementQueryRequest.getUserRole());
        entity.setCurrency(accountStatementQueryRequest.getCurrency());
        entity.setStartTime(accountStatementQueryRequest.getStartTime());
        entity.setEndTime(accountStatementQueryRequest.getEndTime());
        entity.setPageSize(accountStatementQueryRequest.getPageSize());
        entity.setPageNo(accountStatementQueryRequest.getPageNo());

        AccountStatementsResponse response = new AccountStatementsResponse();
        try {
            Integer totalNumber = accountStatementsMapper.countByQuery(entity);
            List<AccountStatementsDto> accountStatementsDtoList = accountStatementsMapper.pageByQuery(entity);

            response.setAccountStatementsDtoList(accountStatementsDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("accountStatementsMapper pageByQuery failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryMerchantRechargeData end");
        return response;
    }

    @Override
    public CommonResponse createAccountStatement(AccountStatementAddRequest accountStatementAddRequest) {
        log.info("createAccountStatement start");

        if (accountStatementAddRequest.getOrderType() == 2) {
            userService.validateWithdrawalPermission(
                    accountStatementAddRequest.getMerchantAgentId(),
                    accountStatementAddRequest.getClientIp());
        }
        AccountStatementsDto accountStatementsDto = generateAccountStatementForAdd(accountStatementAddRequest);

        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.insert(accountStatementsDto);
            // recharge
            if (accountStatementsDto.getOrderType() == 1) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getId(), () -> {
                    balanceService.creditBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount());
                });
            }
            // withdrawal
            if (accountStatementsDto.getOrderType() == 2) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getId(), () -> {
                    balanceService.applyWithdrawalOperation(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount(),
                            0);
                });
            }
            // adjust
            if (accountStatementsDto.getOrderType() == 3) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getId(), () -> {
                    balanceService.adjustBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount());
                });
            }
        });

        // only withdraw order need to resolve
        if (accountStatementsDto.getOrderType() == 2) {
            log.info("notify admin to resolve order");
            List<String> adminUserIds = userMapper.listUserIdsByRoleId(CommonConstant.ROLE_ADMIN);
            if (adminUserIds == null || adminUserIds.isEmpty()) {
                log.warn("no admin user found, skip notification for statementId={}", accountStatementsDto.getId());
            } else {
                for (String adminUserId : adminUserIds) {
                    Message message = new Message();
                    message.setId(accountStatementsDto.getId());
                    message.setUserId(adminUserId);
                    message.setTimestamp(System.currentTimeMillis());
                    message.setRead(false);
                    message.setPath(NotificationComponentType.Withdraw_Component);
                    message.setTitle(NotificationComponentType.Withdraw_Title);
                    message.setContent(accountStatementsDto.getId());
                    redisUtil.saveMessage(message);
                    sendDmqMessage.sendFanout("user-notify", message);
                }
            }
            try {
                orderInterventionTelegramNotifier.notifyPendingWithdrawOrder(
                        accountStatementsDto.getId(),
                        accountStatementsDto.getCreateTime());
            } catch (Exception e) {
                log.warn("send telegram withdraw notify failed, statementId={}, message={}",
                        accountStatementsDto.getId(), e.getMessage());
            }
        }

        log.info("createAccountStatement end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }


    private AccountStatementsDto generateAccountStatementForAdd(AccountStatementAddRequest req) {
        AccountStatementsDto dto = new AccountStatementsDto();
        long now = System.currentTimeMillis() / 1000;
        String systemTransactionNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        dto.setId(systemTransactionNo);

        PatchBuilderUtil<AccountStatementAddRequest, AccountStatementsDto> b = PatchBuilderUtil.from(req).to(dto)
                .reqStr("merchantAgentId", req::getMerchantAgentId, dto::setUserId)
                .reqStr("merchantAgentName", req::getMerchantAgentName, dto::setName)
                .reqStr("currency", req::getCurrency, dto::setCurrency)
                .reqObj("amount", req::getAmount, dto::setAmount)
                .reqObj("orderType", req::getOrderType, dto::setOrderType)
                .obj(req::getUserRole, dto::setUserRole)
                .ifTrue(2 == req.getOrderType())
                .reqStr("walletAddr", req::getWalletAddr, dto::setWalletAddr)
                .endSkip()
                .str(req::getClientIp, dto::setRequestIp)
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(new ArrayList<>() {{
            add(dto.getUserId());
        }}).getTotalData();
        String currency = dto.getCurrency();
        Map<String, BigDecimal> balanceInfo = cardInfo.get(currency);

        BigDecimal availableBalanceBefore = BigDecimal.ZERO;
        BigDecimal frozenBalanceBefore = BigDecimal.ZERO;
        BigDecimal totalBalanceBefore = BigDecimal.ZERO;
        if (balanceInfo != null && !balanceInfo.isEmpty()) {
            availableBalanceBefore = balanceInfo.getOrDefault("available", BigDecimal.ZERO);
            frozenBalanceBefore = balanceInfo.getOrDefault("frozen", BigDecimal.ZERO);
            totalBalanceBefore = balanceInfo.getOrDefault("total", BigDecimal.ZERO);
        } else {
            balanceService.createBalanceRecord(dto.getUserId(), dto.getCurrency());
        }

        if (req.getOrderType() == 2 && availableBalanceBefore.compareTo(req.getAmount()) < CommonConstant.ZERO) {
            log.warn("insufficient available balance");
            throw new PakGoPayException(ResultCode.FAIL, "insufficient available balance");
        }

        if (2 == req.getOrderType()) {
            dto.setStatus(0);
            dto.setAvailableBalanceAfter(CalcUtil.safeSubtract(availableBalanceBefore, req.getAmount()));
            dto.setFrozenBalanceAfter(CalcUtil.safeAdd(frozenBalanceBefore, req.getAmount()));
            dto.setTotalBalanceAfter(totalBalanceBefore);
        } else {
            dto.setStatus(1);
            dto.setAvailableBalanceAfter(CalcUtil.safeAdd(availableBalanceBefore, req.getAmount()));
            dto.setFrozenBalanceAfter(frozenBalanceBefore);
            dto.setTotalBalanceAfter(CalcUtil.safeAdd(totalBalanceBefore, req.getAmount()));
        }
        dto.setFrozenBalanceBefore(frozenBalanceBefore);
        dto.setAvailableBalanceBefore(availableBalanceBefore);
        dto.setTotalBalanceBefore(totalBalanceBefore);

        return b.build();
    }

    @Override
    public CommonResponse updateAccountStatement(AccountStatementEditRequest request) {
        log.info("editAccountStatement start, id={}", request.getId());

        AccountStatementsDto accountStatementsDto = generateAccountStatement(request);
        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.updateById(accountStatementsDto);

            AccountStatementsDto dto = accountStatementsMapper.selectById(accountStatementsDto.getId());

            CommonUtil.withBalanceLogContext("accountStatement.update", dto.getId(), () -> {
                balanceService.applyWithdrawalOperation(
                        dto.getUserId(),
                        dto.getCurrency(),
                        dto.getAmount(),
                        request.isAgree() ? 1 : 2);
            });
        });

        AccountStatementsDto dto = accountStatementsMapper.selectById(accountStatementsDto.getId());
        if (dto != null && dto.getOrderType() != null && dto.getOrderType() == 2) {
            Message message = new Message();
            message.setId(dto.getId());
            message.setUserId(dto.getUserId());
            message.setTimestamp(System.currentTimeMillis());
            message.setRead(false);
            message.setPath(NotificationComponentType.Withdraw_Component);
            message.setTitle(NotificationComponentType.Withdraw_Result_Title);
            message.setContent(dto.getId());
            redisUtil.saveMessage(message);
            sendDmqMessage.sendFanout("user-notify", message);
            log.info("notify merchant withdraw review completed, statementId={}, merchantUserId={}",
                    dto.getId(), dto.getUserId());
        }

        log.info("editAccountStatement end, id={}", request.getId());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AccountStatementsDto generateAccountStatement(AccountStatementEditRequest request) {
        AccountStatementsDto dto = new AccountStatementsDto();
        dto.setUpdateTime(System.currentTimeMillis() / 1000);
        dto.setStatus(request.isAgree() ? 1 : 2);

        return PatchBuilderUtil.from(request).to(dto)
                .str(request::getId, dto::setId)
                .str(request::getRemark, dto::setRemark)
                .str(request::getUserName, dto::setUpdateBy)
                .build();
    }

    /**
     * Persist one adjustment statement row based on subject/snapshot/audit payload.
     */
    @Override
    public void createAdjustmentStatement(AdjustmentStatementRecord payload) {
        if (payload == null || payload.subject() == null || payload.snapshot() == null || payload.audit() == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        AccountStatementsDto statement = new AccountStatementsDto();
        statement.setId(snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX));
        statement.setOrderType(3);
        // Decompose payload parts for readability.
        AdjustmentStatementRecord.Subject subject = payload.subject();
        AdjustmentStatementRecord.Snapshot snapshot = payload.snapshot();
        AdjustmentStatementRecord.Audit audit = payload.audit();
        statement.setAmount(subject.amount());
        statement.setStatus(1);
        // Fill before/after snapshots.
        BalanceDto before = snapshot.before();
        BalanceDto after = snapshot.after();
        statement.setAvailableBalanceBefore(CalcUtil.defaultBigDecimal(before == null ? null : before.getAvailableBalance()));
        statement.setAvailableBalanceAfter(CalcUtil.defaultBigDecimal(after == null ? null : after.getAvailableBalance()));
        statement.setFrozenBalanceBefore(CalcUtil.defaultBigDecimal(before == null ? null : before.getFrozenBalance()));
        statement.setFrozenBalanceAfter(CalcUtil.defaultBigDecimal(after == null ? null : after.getFrozenBalance()));
        statement.setTotalBalanceBefore(CalcUtil.defaultBigDecimal(before == null ? null : before.getTotalBalance()));
        statement.setTotalBalanceAfter(CalcUtil.defaultBigDecimal(after == null ? null : after.getTotalBalance()));
        // Fill business/audit metadata.
        statement.setRequestIp(audit.requestIp());
        statement.setUserId(subject.userId());
        statement.setUserRole(subject.userRole());
        statement.setName(subject.name());
        statement.setCurrency(subject.currency());
        statement.setCreateTime(now);
        statement.setCreateBy(audit.operator());
        statement.setUpdateTime(now);
        statement.setUpdateBy(audit.operator());
        statement.setRemark(audit.remark());
        accountStatementsMapper.insert(statement);
    }
}
