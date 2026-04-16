package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.AccountStatementEnqueueDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.common.AccountStatementApplyService;
import com.pakgopay.service.common.OrderStatementService;
import com.pakgopay.util.CalcUtil;
import com.pakgopay.util.SnowflakeIdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OrderStatementServiceImpl implements OrderStatementService {

    @Autowired
    private SnowflakeIdService snowflakeIdService;

    @Autowired
    private AccountStatementsMapper accountStatementsMapper;

    @Autowired
    private AccountStatementApplyService accountStatementApplyService;

    /**
     * Persist all merchant/agent statements produced by one collection success.
     */
    @Override
    public void recordCollectionSuccessStatements(CollectionOrderDto collectionOrderDto, MerchantInfoDto merchantInfoDto) {
        if (collectionOrderDto == null) {
            return;
        }
        List<AccountStatementsDto> statements = new ArrayList<>();
        BigDecimal creditAmount = CalcUtil.safeSubtract(
                collectionOrderDto.getActualAmount() == null ? collectionOrderDto.getAmount() : collectionOrderDto.getActualAmount(),
                collectionOrderDto.getMerchantFee());
        appendStatement(statements,
                CommonConstant.STATEMENT_ORDER_TYPE_COLLECTION,
                creditAmount,
                collectionOrderDto.getTransactionNo(),
                collectionOrderDto.getMerchantUserId(),
                CommonConstant.STATEMENT_ROLE_MERCHANT,
                merchantInfoDto == null ? null : merchantInfoDto.getMerchantName(),
                collectionOrderDto.getCurrencyType(),
                "SYSTEM",
                "order:collection_credit:mch:" + collectionOrderDto.getTransactionNo());
        appendAgentStatements(
                statements,
                CommonConstant.STATEMENT_ORDER_TYPE_COLLECTION,
                collectionOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                collectionOrderDto.getCurrencyType(),
                collectionOrderDto.getAgent1Fee(),
                collectionOrderDto.getAgent2Fee(),
                collectionOrderDto.getAgent3Fee());
        insertStatements(statements);
    }

    /**
     * Persist all merchant/agent statements produced by one payout success.
     */
    @Override
    public void recordPayoutSuccessStatements(PayOrderDto payOrderDto, MerchantInfoDto merchantInfoDto, BigDecimal frozenAmount) {
        if (payOrderDto == null) {
            return;
        }
        List<AccountStatementsDto> statements = new ArrayList<>();
        appendStatement(statements,
                CommonConstant.STATEMENT_ORDER_TYPE_PAYOUT,
                frozenAmount,
                payOrderDto.getTransactionNo(),
                payOrderDto.getMerchantUserId(),
                CommonConstant.STATEMENT_ROLE_MERCHANT,
                merchantInfoDto == null ? null : merchantInfoDto.getMerchantName(),
                payOrderDto.getCurrencyType(),
                "SYSTEM",
                "order:payout_confirm:mch:" + payOrderDto.getTransactionNo());
        appendAgentStatements(
                statements,
                CommonConstant.STATEMENT_ORDER_TYPE_PAYOUT,
                payOrderDto.getTransactionNo(),
                merchantInfoDto == null ? null : merchantInfoDto.getAgentInfos(),
                payOrderDto.getCurrencyType(),
                payOrderDto.getAgent1Fee(),
                payOrderDto.getAgent2Fee(),
                payOrderDto.getAgent3Fee());
        insertStatements(statements);
    }

    /**
     * Append the three optional agent credit rows when agent fees exist.
     */
    private void appendAgentStatements(List<AccountStatementsDto> statements,
                                       Integer orderType,
                                       String transactionNo,
                                       List<AgentInfoDto> agentInfos,
                                       String currency,
                                       BigDecimal agent1Fee,
                                       BigDecimal agent2Fee,
                                       BigDecimal agent3Fee) {
        if (agentInfos == null || agentInfos.isEmpty()) {
            return;
        }
        appendOneAgentStatement(statements, orderType, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_FIRST, agent1Fee, currency);
        appendOneAgentStatement(statements, orderType, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_SECOND, agent2Fee, currency);
        appendOneAgentStatement(statements, orderType, transactionNo, agentInfos, CommonConstant.AGENT_LEVEL_THIRD, agent3Fee, currency);
    }

    /**
     * Append one agent statement for the specified hierarchy level.
     */
    private void appendOneAgentStatement(List<AccountStatementsDto> statements,
                                         Integer orderType,
                                         String transactionNo,
                                         List<AgentInfoDto> agentInfos,
                                         Integer level,
                                         BigDecimal fee,
                                         String currency) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        for (AgentInfoDto agent : agentInfos) {
            if (agent != null && level.equals(agent.getLevel())) {
                appendStatement(statements,
                        orderType,
                        fee,
                        transactionNo,
                        agent.getUserId(),
                        CommonConstant.STATEMENT_ROLE_AGENT,
                        agent.getAgentName(),
                        currency,
                        "SYSTEM",
                        "order:agent_credit:l" + level + ":" + transactionNo);
                return;
            }
        }
    }

    /**
     * Build one base account_statements row. Real balance apply and snapshot backfill both happen asynchronously.
     */
    private void appendStatement(List<AccountStatementsDto> statements,
                                 Integer orderType,
                                 BigDecimal amount,
                                 String transactionNo,
                                 String userId,
                                 Integer userRole,
                                 String name,
                                 String currency,
                                 String operator,
                                 String remark) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AccountStatementsDto statement = new AccountStatementsDto();
        String serialNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        statement.setSerialNo(serialNo);
        statement.setTransactionNo(transactionNo);
        statement.setOrderType(orderType);
        statement.setAmount(amount);
        statement.setStatus(0);
        statement.setUserId(userId);
        statement.setUserRole(userRole);
        statement.setName(name);
        statement.setCurrency(currency);
        statement.setCreateBy(operator);
        statement.setUpdateTime(System.currentTimeMillis() / 1000);
        statement.setUpdateBy(operator);
        statement.setRemark(remark);
        statements.add(statement);
    }

    /**
     * Insert base rows and enqueue their user+currency keys for async apply.
     */
    private void insertStatements(List<AccountStatementsDto> statements) {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        List<AccountStatementEnqueueDto> insertedRows = accountStatementsMapper.batchInsertReturning(statements);
        String transactionNo = statements.get(0).getTransactionNo();
        log.info("account statement inserted, transactionNo={}, statements={}, enqueueRows={}",
                transactionNo, statements.size(), insertedRows == null ? 0 : insertedRows.size());
        accountStatementApplyService.enqueuePendingApplyRows(insertedRows);
    }

}
