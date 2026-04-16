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
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.service.common.OrderInterventionTelegramNotifier;
import com.pakgopay.service.common.SendDmqMessage;
import com.pakgopay.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AccountStatementServiceImpl extends AbstractStatementSnapshotService implements AccountStatementService {

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
    private SendDmqMessage sendDmqMessage;

    @Autowired
    private OrderInterventionTelegramNotifier orderInterventionTelegramNotifier;

    /**
     * Query statement rows from the new account_statements chain.
     */
    @Override
    public CommonResponse queryAccountStatement(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("queryAccountStatement start");
        AccountStatementsResponse response = loadStatementPage(
                accountStatementQueryRequest);
        log.info("query AccountStatement end");
        return CommonResponse.success(response);
    }

    private AccountStatementsResponse loadStatementPage(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("loadStatementPage start");
        AccountStatementEntity entity = new AccountStatementEntity();
        entity.setSerialNo(accountStatementQueryRequest.getSerialNo());
        entity.setTransactionNo(accountStatementQueryRequest.getTransactionNo());
        entity.setUserId(accountStatementQueryRequest.getMerchantAgentId());
        entity.setOrderType(accountStatementQueryRequest.getOrderType());
        entity.setUserRole(accountStatementQueryRequest.getUserRole());
        entity.setCurrency(accountStatementQueryRequest.getCurrency());
        entity.setStartTime(accountStatementQueryRequest.getStartTime());
        entity.setEndTime(accountStatementQueryRequest.getEndTime());
        entity.setPageSize(accountStatementQueryRequest.getPageSize());
        entity.setPageNo(accountStatementQueryRequest.getPageNo());
        applyDefaultMonthRange(entity);

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
        log.info("loadStatementPage end");
        return response;
    }

    /**
     * Create one manual statement row and apply the real balance change immediately.
     * Snapshot columns are not filled here; they are backfilled asynchronously when status=3.
     */
    @Override
    public CommonResponse createAccountStatement(AccountStatementAddRequest accountStatementAddRequest) {
        log.info("createAccountStatement start");

        if (accountStatementAddRequest.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW) {
            userService.validateWithdrawalPermission(
                    accountStatementAddRequest.getMerchantAgentId(),
                    accountStatementAddRequest.getClientIp());
        }
        AccountStatementsDto accountStatementsDto = generateAccountStatementForAdd(accountStatementAddRequest);
        AccountStatementsDto createdStatement;

        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.insert(accountStatementsDto);
            // Recharge: real credit is applied first, snapshots are backfilled later.
            if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_RECHARGE) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getSerialNo(), () -> {
                    balanceService.creditBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount(),
                            null);
                });
            }
            // Withdrawal apply: move available to frozen first, then wait for manual review.
            if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getSerialNo(), () -> {
                    balanceService.applyWithdrawalOperation(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount(),
                            0,
                            null);
                });
            }
            // Manual adjustment: real balance changes first, snapshots are backfilled later.
            if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_ADJUST) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getSerialNo(), () -> {
                    balanceService.adjustBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount(),
                            null);
                });
            }
        });
        createdStatement = loadStatementBySerialNo(accountStatementsDto.getSerialNo());

        if (accountStatementsDto.getStatus() != null && accountStatementsDto.getStatus() == 3) {
            enqueuePendingSnapshot(
                    accountStatementsDto.getUserId(),
                    accountStatementsDto.getCurrency(),
                    createdStatement == null ? accountStatementsDto.getCreateTime() : createdStatement.getCreateTime());
        }

        // only withdraw order need to resolve
        if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW) {
            log.info("notify admin to resolve order");
            List<String> adminUserIds = userMapper.listUserIdsByRoleId(CommonConstant.ROLE_ADMIN);
            if (adminUserIds == null || adminUserIds.isEmpty()) {
                log.warn("no admin user found, skip notification for statementId={}", accountStatementsDto.getSerialNo());
            } else {
                for (String adminUserId : adminUserIds) {
                    Message message = new Message();
                    message.setId(accountStatementsDto.getSerialNo());
                    message.setUserId(adminUserId);
                    message.setTimestamp(System.currentTimeMillis());
                    message.setRead(false);
                    message.setPath(NotificationComponentType.Withdraw_Component);
                    message.setTitle(NotificationComponentType.Withdraw_Title);
                    message.setContent(accountStatementsDto.getSerialNo());
                    redisUtil.saveMessage(message);
                    sendDmqMessage.sendFanout("user-notify", message);
                }
            }
            try {
                orderInterventionTelegramNotifier.notifyPendingWithdrawOrder(
                        accountStatementsDto.getSerialNo(),
                        createdStatement == null ? null : createdStatement.getCreateTime());
            } catch (Exception e) {
                log.warn("send telegram withdraw notify failed, statementId={}, message={}",
                        accountStatementsDto.getSerialNo(), e.getMessage());
            }
        }

        log.info("createAccountStatement end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    /**
     * Build the base row for manual recharge / withdrawal / adjustment.
     */
    private AccountStatementsDto generateAccountStatementForAdd(AccountStatementAddRequest req) {
        AccountStatementsDto dto = new AccountStatementsDto();
        String systemTransactionNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        long createTime = resolveCreateTimeFromSerialNo(systemTransactionNo);
        dto.setSerialNo(systemTransactionNo);

        PatchBuilderUtil<AccountStatementAddRequest, AccountStatementsDto> b = PatchBuilderUtil.from(req).to(dto)
                .reqStr("merchantAgentId", req::getMerchantAgentId, dto::setUserId)
                .reqStr("merchantAgentName", req::getMerchantAgentName, dto::setName)
                .reqStr("currency", req::getCurrency, dto::setCurrency)
                .reqObj("amount", req::getAmount, dto::setAmount)
                .reqObj("orderType", req::getOrderType, dto::setOrderType)
                .obj(req::getUserRole, dto::setUserRole)
                .ifTrue(CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW == req.getOrderType())
                .reqStr("walletAddr", req::getWalletAddr, dto::setWalletAddr)
                .endSkip()
                .str(req::getClientIp, dto::setRequestIp)
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setUpdateTime(createTime);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        if (CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW == req.getOrderType()) {
            Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(new ArrayList<>() {{
                add(dto.getUserId());
            }}).getTotalData();
            String currency = dto.getCurrency();
            Map<String, BigDecimal> balanceInfo = cardInfo.get(currency);

            BigDecimal availableBalanceBefore = BigDecimal.ZERO;
            if (balanceInfo != null && !balanceInfo.isEmpty()) {
                availableBalanceBefore = balanceInfo.getOrDefault("available", BigDecimal.ZERO);
            } else {
                balanceService.createBalanceRecord(dto.getUserId(), dto.getCurrency());
            }

            if (availableBalanceBefore.compareTo(req.getAmount()) < CommonConstant.ZERO) {
                log.warn("insufficient available balance");
                throw new PakGoPayException(ResultCode.FAIL, "insufficient available balance");
            }

            dto.setStatus(0);
        } else {
            dto.setStatus(3);
        }

        return b.build();
    }

    /**
     * Review one withdrawal statement. Approval applies the real payout delta first and then
     * enqueues the async snapshot task to fill before/after balances in order.
     */
    @Override
    public CommonResponse updateAccountStatement(AccountStatementEditRequest request) {
        log.info("editAccountStatement start, serialNo={}", request.getSerialNo());

        AccountStatementsDto accountStatementsDto = generateAccountStatement(request);
        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.updateBySerialNo(accountStatementsDto);

            AccountStatementsDto dto = loadStatementBySerialNo(accountStatementsDto.getSerialNo());

            CommonUtil.withBalanceLogContext("accountStatement.update", dto.getSerialNo(), () -> {
                balanceService.applyWithdrawalOperation(
                        dto.getUserId(),
                        dto.getCurrency(),
                        dto.getAmount(),
                        request.isAgree() ? 1 : 2,
                        null);
            });
        });

        if (Boolean.TRUE.equals(request.isAgree())) {
            AccountStatementsDto approved = loadStatementBySerialNo(accountStatementsDto.getSerialNo());
            if (approved != null) {
                enqueuePendingSnapshot(approved.getUserId(), approved.getCurrency(), approved.getCreateTime());
            }
        }

        AccountStatementsDto dto = loadStatementBySerialNo(accountStatementsDto.getSerialNo());
        if (dto != null && dto.getOrderType() != null && dto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_WITHDRAW) {
            Message message = new Message();
            message.setId(dto.getSerialNo());
            message.setUserId(dto.getUserId());
            message.setTimestamp(System.currentTimeMillis());
            message.setRead(false);
            message.setPath(NotificationComponentType.Withdraw_Component);
            message.setTitle(NotificationComponentType.Withdraw_Result_Title);
            message.setContent(dto.getSerialNo());
            redisUtil.saveMessage(message);
            sendDmqMessage.sendFanout("user-notify", message);
            log.info("notify merchant withdraw review completed, statementId={}, merchantUserId={}",
                    dto.getSerialNo(), dto.getUserId());
        }

        log.info("editAccountStatement end, serialNo={}", request.getSerialNo());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    /**
     * Build the partial update payload for one withdrawal review action.
     */
    private AccountStatementsDto generateAccountStatement(AccountStatementEditRequest request) {
        AccountStatementsDto dto = new AccountStatementsDto();
        dto.setUpdateTime(System.currentTimeMillis() / 1000);
        dto.setStatus(request.isAgree() ? 3 : 2);

        return PatchBuilderUtil.from(request).to(dto)
                .str(request::getSerialNo, dto::setSerialNo)
                .str(request::getRemark, dto::setRemark)
                .str(request::getUserName, dto::setUpdateBy)
                .build();
    }

    /**
     * Persist one adjustment statement row after the real balance change has already committed.
     */
    @Override
    public void createAdjustmentStatement(AdjustmentStatementRecord payload) {
        if (payload == null || payload.subject() == null || payload.audit() == null) {
            return;
        }
        AccountStatementsDto statement = new AccountStatementsDto();
        String serialNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        long createTime = resolveCreateTimeFromSerialNo(serialNo);
        statement.setSerialNo(serialNo);
        statement.setOrderType(CommonConstant.STATEMENT_ORDER_TYPE_ADJUST);
        // Decompose payload parts for readability.
        AdjustmentStatementRecord.Subject subject = payload.subject();
        AdjustmentStatementRecord.Audit audit = payload.audit();
        statement.setAmount(subject.amount());
        statement.setStatus(3);
        // Fill business/audit metadata.
        statement.setRequestIp(audit.requestIp());
        statement.setUserId(subject.userId());
        statement.setUserRole(subject.userRole());
        statement.setName(subject.name());
        statement.setCurrency(subject.currency());
        statement.setCreateBy(audit.operator());
        statement.setUpdateTime(createTime);
        statement.setUpdateBy(audit.operator());
        statement.setRemark(audit.remark());
        accountStatementsMapper.insert(statement);
        AccountStatementsDto inserted = loadStatementBySerialNo(statement.getSerialNo());
        enqueuePendingSnapshot(
                statement.getUserId(),
                statement.getCurrency(),
                inserted == null ? statement.getCreateTime() : inserted.getCreateTime());
    }

    private void applyDefaultMonthRange(AccountStatementEntity entity) {
        if (entity.getStartTime() != null || entity.getEndTime() != null) {
            return;
        }
        String anchorId = entity.getSerialNo() != null && !entity.getSerialNo().isBlank()
                ? entity.getSerialNo()
                : entity.getTransactionNo();
        if (anchorId == null || anchorId.isBlank()) {
            return;
        }
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(anchorId);
        if (range == null || range.length != 2) {
            return;
        }
        entity.setStartTime(range[0]);
        entity.setEndTime(range[1]);
    }

    @Override
    public AccountStatementsDto findBySerialNo(String serialNo) {
        return loadStatementBySerialNo(serialNo);
    }

    private AccountStatementsDto loadStatementBySerialNo(String serialNo) {
        for (String partitionTable : resolveCandidatePartitionTablesBySerialNo(serialNo)) {
            AccountStatementsDto dto = accountStatementsMapper.selectBySerialNoFromTable(partitionTable, serialNo);
            if (dto != null) {
                return dto;
            }
        }
        return null;
    }

}
