package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.dto.AccountStatementEnqueueDto;
import com.pakgopay.mapper.dto.AccountStatementTaskCursorDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementApplyService;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class AccountStatementApplyServiceImpl extends AbstractAccountStatementTaskSupport implements AccountStatementApplyService {

    private static final String TASK_TYPE_APPLY = "apply";
    public static final String PENDING_BALANCE_APPLY_SET_KEY = "job:account_statement:pending_balance_apply";
    private static final String PENDING_BALANCE_APPLY_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_apply:months:";
    private static final String PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_snapshot:months:";

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Value("${pakgopay.account-statement.apply-worker-count:8}")
    private int applyWorkerCount;

    private volatile ExecutorService applyExecutor;

    @Override
    protected String taskType() {
        return TASK_TYPE_APPLY;
    }

    @Override
    protected String taskSetKey() {
        return PENDING_BALANCE_APPLY_SET_KEY;
    }

    @Override
    protected String taskMonthSetKeyPrefix() {
        return PENDING_BALANCE_APPLY_MONTH_SET_KEY_PREFIX;
    }

    @Override
    public void enqueuePendingApplyRows(List<AccountStatementEnqueueDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> dedupKeys = new LinkedHashSet<>();
        for (AccountStatementEnqueueDto row : rows) {
            if (row == null || row.getUserId() == null || row.getCurrency() == null) {
                continue;
            }
            long monthStart = resolveMonthRange(resolveCreateTime(row.getCreateTime()))[0];
            String dedupKey = row.getUserId() + "|" + row.getCurrency() + "|" + formatMonthCode(monthStart);
            if (dedupKeys.add(dedupKey)) {
                enqueuePendingApply(row.getUserId(), row.getCurrency(), row.getCreateTime());
            }
        }
        log.info("account statement apply enqueue, rows={}, accounts={}", rows.size(), dedupKeys.size());
    }

    @Override
    public int processPendingApplies(int accountLimit, int statementLimit) {
        int workerCount = Math.max(1, applyWorkerCount);
        return processPendingTaskCursors(
                accountLimit,
                applyExecutor(workerCount),
                (pendingAccount, cursor) -> processOnePendingApplyAccount(pendingAccount, statementLimit, cursor));
    }

    private void enqueuePendingApply(String userId, String currency, Long createTime) {
        enqueuePendingTask(userId, currency, createTime);
    }

    private int processOnePendingApplyAccount(PendingAccount pendingAccount,
                                              int statementLimit,
                                              AccountStatementTaskCursorDto cursor) {
        String monthCode = resolveOrReseedMonthCode(
                pendingAccount,
                cursor == null ? null : cursor.getPendingMonth(),
                account -> accountStatementsMapper.selectEarliestPendingApplyAnchor(account.userId(), account.currency()));
        if (monthCode == null) {
            log.info("account statement apply no anchor, account={}", pendingAccount.rawKey());
            return 0;
        }
        return processOnePendingApplyMonth(
                pendingAccount,
                statementLimit,
                monthCode,
                cursor == null ? null : cursor.getLastDoneSeq());
    }

    private int processOnePendingApplyMonth(PendingAccount pendingAccount,
                                            int statementLimit,
                                            String monthCode,
                                            Long lastDoneSeq) {
        long monthStartEpochSecond = parseMonthCodeToEpochSecond(monthCode);
        long[] monthRange = resolveMonthRange(monthStartEpochSecond);
        String partitionTable = resolvePartitionTable(monthStartEpochSecond);
        List<AccountStatementsDto> pendingStatements = accountStatementsMapper.listPendingBalanceApplyFromTable(
                partitionTable,
                pendingAccount.userId(),
                pendingAccount.currency(),
                monthRange[0],
                monthRange[1],
                statementLimit + 1);
        if (pendingStatements == null || pendingStatements.isEmpty()) {
            refreshCursorFromDb(
                    pendingAccount,
                    lastDoneSeq,
                    account -> accountStatementsMapper.selectEarliestPendingApplyAnchor(account.userId(), account.currency()));
            log.info("account statement apply month empty, account={}, month={}, table={}",
                    pendingAccount.rawKey(), monthCode, partitionTable);
            return 0;
        }

        boolean hasMoreInMonth = pendingStatements.size() > statementLimit;
        List<AccountStatementsDto> statementsToProcess = hasMoreInMonth
                ? new ArrayList<>(pendingStatements.subList(0, statementLimit))
                : pendingStatements;
        int updated = applyBalanceForStatements(statementsToProcess);
        log.info("account statement apply batch, account={}, month={}, table={}, fetched={}, processing={}, updated={}, hasMore={}, firstId={}, lastId={}",
                pendingAccount.rawKey(), monthCode, partitionTable, pendingStatements.size(), statementsToProcess.size(),
                updated, hasMoreInMonth, statementsToProcess.get(0).getId(),
                statementsToProcess.get(statementsToProcess.size() - 1).getId());

        if (hasMoreInMonth) {
            upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), monthCode, null);
            redisUtil.addSetMember(taskSetKey(), pendingAccount.rawKey());
            return updated;
        }

        refreshCursorFromDb(
                pendingAccount,
                null,
                account -> accountStatementsMapper.selectEarliestPendingApplyAnchor(account.userId(), account.currency()));
        return updated;
    }

    private int applyBalanceForStatements(List<AccountStatementsDto> statements) {
        if (statements == null || statements.isEmpty()) {
            return 0;
        }
        List<AccountStatementsDto> applied = new ArrayList<>();
        int failed = 0;
        for (AccountStatementsDto statement : statements) {
            try {
                transactionUtil.runInTransaction(() -> applyOneBalanceStatement(statement));
                statement.setStatus(3);
                applied.add(statement);
                enqueuePendingSnapshot(statement.getUserId(), statement.getCurrency(), statement.getCreateTime());
            } catch (Exception e) {
                failed++;
                log.warn("account statement apply one failed, serialNo={}, id={}, userId={}, currency={}, remark={}, message={}",
                        statement.getSerialNo(), statement.getId(), statement.getUserId(), statement.getCurrency(),
                        statement.getRemark(), e.getMessage());
            }
        }
        if (applied.isEmpty()) {
            log.warn("account statement apply batch no success, size={}, failed={}", statements.size(), failed);
            return 0;
        }
        int updated = accountStatementsMapper.batchUpdateStatusBySerialNo(applied);
        log.info("account statement apply status updated, success={}, failed={}, updated={}",
                applied.size(), failed, updated);
        return updated;
    }

    private void applyOneBalanceStatement(AccountStatementsDto statement) {
        Integer orderType = statement.getOrderType();
        if (orderType == null || (orderType != CommonConstant.STATEMENT_ORDER_TYPE_COLLECTION_CREDIT
                && orderType != CommonConstant.STATEMENT_ORDER_TYPE_PAYOUT_CONFIRM)) {
            throw new IllegalStateException("unsupported async apply orderType: " + orderType);
        }
        String remark = statement.getRemark() == null ? "" : statement.getRemark();
        if (remark.startsWith("order:payout_confirm")) {
            balanceService.confirmPayoutBalance(statement.getUserId(), statement.getCurrency(), statement.getAmount(), null);
            return;
        }
        if (remark.startsWith("order:collection_credit") || remark.startsWith("order:agent_credit")) {
            balanceService.creditBalance(statement.getUserId(), statement.getCurrency(), statement.getAmount(), null);
            return;
        }
        throw new IllegalStateException("unsupported async apply statement remark: " + remark);
    }

    private void enqueuePendingSnapshot(String userId, String currency, Long createTime) {
        enqueuePendingTask(
                userId,
                currency,
                createTime,
                "snapshot",
                PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX);
    }

    private ExecutorService applyExecutor(int workerCount) {
        ExecutorService current = applyExecutor;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (applyExecutor == null) {
                applyExecutor = Executors.newFixedThreadPool(workerCount);
            }
            return applyExecutor;
        }
    }
}
