package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.account.AccountStatementEntity;
import com.pakgopay.data.entity.account.AdjustmentStatementRecord;
import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.account.AccountStatementsResponse;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AccountStatementServiceImpl extends AbstractStatementSnapshotService implements AccountStatementService {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private SnowflakeIdService snowflakeIdService;

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

        if (accountStatementAddRequest.getOrderType() != CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_CREDIT
                && accountStatementAddRequest.getOrderType() != CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_DEBIT) {
            throw new PakGoPayException(ResultCode.FAIL, "manual statement only supports orderType 41 or 42");
        }
        AccountStatementsDto accountStatementsDto = generateAccountStatementForAdd(accountStatementAddRequest);
        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.insert(accountStatementsDto);
            if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_CREDIT) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getSerialNo(), () -> {
                    balanceService.creditBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount(),
                            null);
                });
            }
            if (accountStatementsDto.getOrderType() == CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_DEBIT) {
                CommonUtil.withBalanceLogContext("accountStatement.create", accountStatementsDto.getSerialNo(), () -> {
                    balanceService.adjustBalance(
                            accountStatementsDto.getUserId(),
                            accountStatementsDto.getCurrency(),
                            accountStatementsDto.getAmount().negate(),
                            null);
                });
            }
            AccountStatementsDto inserted = loadStatementBySerialNo(accountStatementsDto.getSerialNo());
            enqueuePendingSnapshot(
                    accountStatementsDto.getUserId(),
                    accountStatementsDto.getCurrency(),
                    inserted == null ? accountStatementsDto.getCreateTime() : inserted.getCreateTime());
        });

        log.info("createAccountStatement end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    /**
     * Build the base row for manual credit / debit.
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
                .str(req::getClientIp, dto::setRequestIp)
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setUpdateTime(createTime);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());
        if (CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_DEBIT == req.getOrderType()) {
            Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(List.of(dto.getUserId())).getTotalData();
            Map<String, BigDecimal> balanceInfo = cardInfo.get(dto.getCurrency());
            BigDecimal availableBalanceBefore = balanceInfo == null
                    ? BigDecimal.ZERO
                    : balanceInfo.getOrDefault("available", BigDecimal.ZERO);
            if (availableBalanceBefore.compareTo(req.getAmount()) < 0) {
                log.warn("insufficient available balance");
                throw new PakGoPayException(ResultCode.FAIL, "insufficient available balance");
            }
        }
        dto.setStatus(3);
        return b.build();
    }

    /**
     * Persist one manual credit/debit statement row after the real balance change has already committed.
     */
    @Override
    public void createAdjustmentStatement(AdjustmentStatementRecord payload) {
        if (payload == null || payload.subject() == null || payload.audit() == null) {
            return;
        }
        AccountStatementsDto statement = new AccountStatementsDto();
        String serialNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        long createTime = resolveCreateTimeFromSerialNo(serialNo);
        BigDecimal rawAmount = payload.subject().amount();
        BigDecimal normalizedAmount = rawAmount == null ? null : rawAmount.abs();
        statement.setSerialNo(serialNo);
        statement.setOrderType(rawAmount != null && rawAmount.compareTo(BigDecimal.ZERO) >= 0
                ? CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_CREDIT
                : CommonConstant.STATEMENT_ORDER_TYPE_MANUAL_DEBIT);
        // Decompose payload parts for readability.
        AdjustmentStatementRecord.Subject subject = payload.subject();
        AdjustmentStatementRecord.Audit audit = payload.audit();
        statement.setAmount(normalizedAmount);
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
        return super.loadStatementBySerialNo(serialNo);
    }

}
