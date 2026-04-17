package com.pakgopay.service.impl;

import com.pakgopay.mapper.dto.AccountStatementTaskCursorDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.timer.PartitionMaintenanceTimer;
import com.pakgopay.util.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public abstract class AbstractStatementSnapshotService extends AbstractAccountStatementTaskSupport {

    private static final String TASK_TYPE_SNAPSHOT = "snapshot";
    public static final String PENDING_BALANCE_SNAPSHOT_SET_KEY = "job:account_statement:pending_balance_snapshot";
    private static final String PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_snapshot:months:";
    private static final int SNAPSHOT_UPDATE_BATCH_SIZE = 50;
    private static final int SNAPSHOT_LOOKBACK_MONTHS = 12;

    @Value("${pakgopay.account-statement.snapshot-worker-count:8}")
    private int snapshotWorkerCount;

    private volatile ExecutorService snapshotExecutor;

    @Override
    protected String taskType() {
        return TASK_TYPE_SNAPSHOT;
    }

    @Override
    protected String taskSetKey() {
        return PENDING_BALANCE_SNAPSHOT_SET_KEY;
    }

    @Override
    protected String taskMonthSetKeyPrefix() {
        return PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX;
    }

    /**
     * Add one user+currency task into the deduplicated pending snapshot set.
     * Month partition is resolved later from the earliest pending row so one account never fans out into multiple hot keys.
     */
    public void enqueuePendingSnapshot(String userId, String currency, Long createTime) {
        enqueuePendingTask(userId, currency, createTime);
    }

    /**
     * Consume up to {@code accountLimit} accounts this round, and process up to
     * {@code statementLimit} statement rows per account.
     */
    public int processPendingSnapshots(int accountLimit, int statementLimit) {
        int workerCount = Math.max(1, snapshotWorkerCount);
        return processPendingTaskCursors(
                accountLimit,
                snapshotExecutor(workerCount),
                (pendingAccount, cursor) -> processOnePendingAccount(pendingAccount, statementLimit, cursor));
    }

    private int processOnePendingAccount(PendingAccount pendingAccount,
                                         int statementLimit,
                                         AccountStatementTaskCursorDto cursor) {
        String monthCode = resolveOrReseedMonthCode(
                pendingAccount,
                cursor == null ? null : cursor.getPendingMonth(),
                account -> accountStatementsMapper.selectEarliestPendingSnapshotAnchor(account.userId(), account.currency()));
        if (monthCode == null) {
            log.info("account statement snapshot no anchor, account={}", pendingAccount.rawKey());
            return 0;
        }
        return processOnePendingAccountMonth(
                pendingAccount,
                statementLimit,
                monthCode,
                cursor == null ? null : cursor.getLastDoneSeq());
    }

    private int processOnePendingAccountMonth(PendingAccount pendingAccount,
                                              int statementLimit,
                                              String monthCode,
                                              Long lastDoneSeq) {
        long monthStartEpochSecond = parseMonthCodeToEpochSecond(monthCode);
        long[] monthRange = resolveMonthRange(monthStartEpochSecond);
        String partitionTable = resolvePartitionTable(monthStartEpochSecond);
        List<AccountStatementsDto> pendingStatements = accountStatementsMapper.listPendingBalanceSnapshotsFromTable(
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
                    account -> accountStatementsMapper.selectEarliestPendingSnapshotAnchor(account.userId(), account.currency()));
            log.info("account statement snapshot month empty, account={}, month={}, table={}",
                    pendingAccount.rawKey(), monthCode, partitionTable);
            return 0;
        }

        boolean hasMoreInMonth = pendingStatements.size() > statementLimit;
        List<AccountStatementsDto> statementsToProcess = hasMoreInMonth
                ? new ArrayList<>(pendingStatements.subList(0, statementLimit))
                : pendingStatements;
        int updated = applySnapshotsForAccount(
                pendingAccount.userId(),
                pendingAccount.currency(),
                monthRange,
                lastDoneSeq,
                statementsToProcess);
        log.info("account statement snapshot batch, account={}, month={}, table={}, fetched={}, processing={}, updated={}, hasMore={}, firstSeq={}, lastSeq={}",
                pendingAccount.rawKey(), monthCode, partitionTable, pendingStatements.size(), statementsToProcess.size(),
                updated, hasMoreInMonth, statementsToProcess.get(0).getAccountSeq(),
                statementsToProcess.get(statementsToProcess.size() - 1).getAccountSeq());

        if (hasMoreInMonth) {
            Long batchLastSeq = statementsToProcess.get(statementsToProcess.size() - 1).getAccountSeq();
            upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), monthCode, batchLastSeq);
            redisUtil.addSetMember(taskSetKey(), pendingAccount.rawKey());
            return updated;
        }

        Long batchLastSeq = statementsToProcess.get(statementsToProcess.size() - 1).getAccountSeq();
        refreshCursorFromDb(
                pendingAccount,
                batchLastSeq,
                account -> accountStatementsMapper.selectEarliestPendingSnapshotAnchor(account.userId(), account.currency()));
        return updated;
    }

    /**
     * Backfill one ordered batch for the same user+currency using the latest completed row as baseline.
     * Ordering is defined by account_seq within the same userId+currency.
     */
    private int applySnapshotsForAccount(String userId,
                                         String currency,
                                         long[] currentMonthRange,
                                         Long lastDoneSeq,
                                         List<AccountStatementsDto> pendingStatements) {
        long nextSeq = resolveNextSnapshotSeq(userId, currency, lastDoneSeq);
        for (AccountStatementsDto statement : pendingStatements) {
            statement.setAccountSeq(nextSeq++);
        }
        AccountStatementsDto latestCompleted = findLatestCompletedBefore(
                userId,
                currency,
                pendingStatements.get(0).getAccountSeq(),
                currentMonthRange[0]);
        BigDecimal availableBefore = latestCompleted == null || latestCompleted.getAvailableBalanceAfter() == null
                ? BigDecimal.ZERO : latestCompleted.getAvailableBalanceAfter();
        BigDecimal frozenBefore = latestCompleted == null || latestCompleted.getFrozenBalanceAfter() == null
                ? BigDecimal.ZERO : latestCompleted.getFrozenBalanceAfter();
        BigDecimal totalBefore = latestCompleted == null || latestCompleted.getTotalBalanceAfter() == null
                ? BigDecimal.ZERO : latestCompleted.getTotalBalanceAfter();
        List<AccountStatementsDto> toUpdate = new ArrayList<>(pendingStatements.size());
        for (AccountStatementsDto statement : pendingStatements) {
            SnapshotDelta delta = resolveSnapshotDelta(statement);
            if (delta == null) {
                statement.setStatus(2);
                toUpdate.add(statement);
                log.warn("account statement snapshot delta unresolved, serialNo={}, id={}, userId={}, currency={}, orderType={}, remark={}",
                        statement.getSerialNo(), statement.getId(), statement.getUserId(), statement.getCurrency(),
                        statement.getOrderType(), statement.getRemark());
                continue;
            }
            BigDecimal availableAfter = availableBefore.add(delta.availableDelta());
            BigDecimal frozenAfter = frozenBefore.add(delta.frozenDelta());
            BigDecimal totalAfter = totalBefore.add(delta.totalDelta());
            statement.setAvailableBalanceBefore(availableBefore);
            statement.setAvailableBalanceAfter(availableAfter);
            statement.setFrozenBalanceBefore(frozenBefore);
            statement.setFrozenBalanceAfter(frozenAfter);
            statement.setTotalBalanceBefore(totalBefore);
            statement.setTotalBalanceAfter(totalAfter);
            statement.setStatus(1);
            toUpdate.add(statement);
            availableBefore = availableAfter;
            frozenBefore = frozenAfter;
            totalBefore = totalAfter;
        }
        if (toUpdate.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (int from = 0; from < toUpdate.size(); from += SNAPSHOT_UPDATE_BATCH_SIZE) {
            int to = Math.min(from + SNAPSHOT_UPDATE_BATCH_SIZE, toUpdate.size());
            updated += accountStatementsMapper.batchUpdateBySerialNo(toUpdate.subList(from, to));
        }
        log.info("account statement snapshot status updated, account={}|{}, size={}, updated={}",
                userId, currency, toUpdate.size(), updated);
        return updated;
    }

    private long resolveNextSnapshotSeq(String userId, String currency, Long lastDoneSeq) {
        if (lastDoneSeq != null && lastDoneSeq > 0) {
            return lastDoneSeq + 1;
        }
        Long maxSeq = accountStatementsMapper.selectMaxAccountSeq(userId, currency);
        return maxSeq == null || maxSeq <= 0 ? 1L : maxSeq + 1;
    }

    /**
     * Search the latest completed baseline from the current pending month backwards month by month.
     */
    private AccountStatementsDto findLatestCompletedBefore(String userId,
                                                           String currency,
                                                           Long accountSeq,
                                                           long monthStartEpochSecond) {
        List<String> knownMonths = loadKnownAccountStatementPartitionMonths();
        if (!knownMonths.isEmpty()) {
            String earliestMonthCode = knownMonths.get(0);
            long probeMonthStart = monthStartEpochSecond;
            while (formatMonthCode(probeMonthStart).compareTo(earliestMonthCode) >= 0) {
                String monthCode = formatMonthCode(probeMonthStart);
                if (!knownMonths.contains(monthCode)) {
                    probeMonthStart = previousMonthStart(probeMonthStart);
                    continue;
                }
                AccountStatementsDto latestCompleted = selectLatestCompletedBeforeSafely(
                        userId, currency, accountSeq, probeMonthStart);
                if (latestCompleted != null) {
                    return latestCompleted;
                }
                probeMonthStart = previousMonthStart(probeMonthStart);
            }
            return null;
        }

        long probeMonthStart = monthStartEpochSecond;
        for (int i = 0; i < SNAPSHOT_LOOKBACK_MONTHS; i++) {
            AccountStatementsDto latestCompleted = selectLatestCompletedBeforeSafely(
                    userId, currency, accountSeq, probeMonthStart);
            if (latestCompleted != null) {
                return latestCompleted;
            }
            probeMonthStart = previousMonthStart(probeMonthStart);
        }
        return null;
    }

    private List<String> loadPendingMonths(String monthSetKey) {
        Set<String> months = redisUtil.getSetMembers(monthSetKey);
        if (months == null || months.isEmpty()) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(months);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private List<String> loadKnownAccountStatementPartitionMonths() {
        return loadPendingMonths(PartitionMaintenanceTimer.ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY);
    }

    private AccountStatementsDto selectLatestCompletedBeforeSafely(String userId,
                                                                   String currency,
                                                                   Long accountSeq,
                                                                   long probeMonthStart) {
        long[] monthRange = resolveMonthRange(probeMonthStart);
        String partitionTable = resolvePartitionTable(monthRange[0]);
        try {
            return accountStatementsMapper.selectLatestCompletedBeforeFromTable(
                    partitionTable,
                    userId,
                    currency,
                    monthRange[0],
                    monthRange[1],
                    accountSeq);
        } catch (BadSqlGrammarException e) {
            String message = e.getMessage();
            if (message != null && message.contains("does not exist")) {
                log.info("account statement snapshot baseline partition missing, table={}", partitionTable);
                return null;
            }
            throw e;
        }
    }

    private ExecutorService snapshotExecutor(int workerCount) {
        ExecutorService current = snapshotExecutor;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (snapshotExecutor == null) {
                // Keep a small fixed pool; cross-account parallelism is enough because the timer already holds a global lock.
                snapshotExecutor = Executors.newFixedThreadPool(workerCount);
            }
            return snapshotExecutor;
        }
    }

    @PreDestroy
    public void destroySnapshotExecutor() {
        ExecutorService current = snapshotExecutor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    protected SnapshotDelta resolveSnapshotDelta(AccountStatementsDto statement) {
        if (statement == null || statement.getAmount() == null) {
            return null;
        }
        BigDecimal amount = statement.getAmount();
        String remark = statement.getRemark() == null ? "" : statement.getRemark();
        if (remark.startsWith("order:payout_freeze")) {
            return new SnapshotDelta(amount.negate(), amount, BigDecimal.ZERO);
        }
        if (remark.startsWith("order:payout_release")) {
            return new SnapshotDelta(amount, amount.negate(), BigDecimal.ZERO);
        }
        if (remark.startsWith("order:payout_confirm")) {
            return new SnapshotDelta(BigDecimal.ZERO, amount.negate(), amount.negate());
        }
        if (remark.startsWith("order:collection_credit") || remark.startsWith("order:agent_credit")) {
            return new SnapshotDelta(amount, BigDecimal.ZERO, amount);
        }
        if (statement.getOrderType() == null) {
            return null;
        }
        return switch (statement.getOrderType()) {
            case 11 -> new SnapshotDelta(amount, BigDecimal.ZERO, amount);
            case 21 -> new SnapshotDelta(amount.negate(), amount, BigDecimal.ZERO);
            case 22 -> new SnapshotDelta(amount, amount.negate(), BigDecimal.ZERO);
            case 23 -> new SnapshotDelta(BigDecimal.ZERO, amount.negate(), amount.negate());
            case 31 -> new SnapshotDelta(amount.negate(), amount, BigDecimal.ZERO);
            case 32 -> new SnapshotDelta(amount, amount.negate(), BigDecimal.ZERO);
            case 33 -> new SnapshotDelta(BigDecimal.ZERO, amount.negate(), amount.negate());
            case 41 -> new SnapshotDelta(amount, BigDecimal.ZERO, amount);
            case 42 -> new SnapshotDelta(amount.negate(), BigDecimal.ZERO, amount.negate());
            default -> null;
        };
    }

    protected record SnapshotDelta(BigDecimal availableDelta, BigDecimal frozenDelta, BigDecimal totalDelta) {
    }

    protected List<String> resolveCandidatePartitionTablesBySerialNo(String serialNo) {
        long createTime = resolveCreateTimeFromSerialNo(serialNo);
        long[] monthRange = resolveMonthRange(createTime);
        long currentMonthStart = monthRange[0];
        long currentMonthEnd = monthRange[1];
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(resolvePartitionTable(currentMonthStart));

        long middleTime = currentMonthStart + (currentMonthEnd - currentMonthStart) / 2;
        boolean firstHalfOfMonth = createTime < middleTime;

        if (firstHalfOfMonth) {
            candidates.add(resolvePartitionTable(previousMonthStart(currentMonthStart)));
            candidates.add(resolvePartitionTable(nextMonthStart(currentMonthStart)));
        } else {
            candidates.add(resolvePartitionTable(nextMonthStart(currentMonthStart)));
            candidates.add(resolvePartitionTable(previousMonthStart(currentMonthStart)));
        }

        return new ArrayList<>(candidates);
    }

    protected AccountStatementsDto loadStatementBySerialNo(String serialNo) {
        for (String partitionTable : resolveCandidatePartitionTablesBySerialNo(serialNo)) {
            AccountStatementsDto dto = accountStatementsMapper.selectBySerialNoFromTable(partitionTable, serialNo);
            if (dto != null) {
                return dto;
            }
        }
        return null;
    }

    protected long resolveCreateTimeFromSerialNo(String serialNo) {
        Long epochSecond = SnowflakeIdGenerator.extractEpochSecondFromPrefixedId(serialNo);
        if (epochSecond != null && epochSecond > 0) {
            return epochSecond;
        }
        return System.currentTimeMillis() / 1000;
    }
}
