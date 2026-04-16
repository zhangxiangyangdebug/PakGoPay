package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.dto.AccountStatementEnqueueDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementApplyService;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
public class AccountStatementApplyServiceImpl implements AccountStatementApplyService {

    public static final String PENDING_BALANCE_APPLY_SET_KEY = "job:account_statement:pending_balance_apply";
    private static final String PENDING_BALANCE_APPLY_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_apply:months:";
    private static final String PENDING_BALANCE_SNAPSHOT_SET_KEY = AbstractStatementSnapshotService.PENDING_BALANCE_SNAPSHOT_SET_KEY;
    private static final String PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_snapshot:months:";
    private static final int SNAPSHOT_MONTH_SET_EXPIRE_SECONDS = 86400;

    @Autowired
    private AccountStatementsMapper accountStatementsMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Value("${pakgopay.account-statement.apply-worker-count:8}")
    private int applyWorkerCount;

    private volatile ExecutorService applyExecutor;

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
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < accountLimit; i++) {
            String rawPendingAccount = redisUtil.popSetMember(PENDING_BALANCE_APPLY_SET_KEY);
            if (rawPendingAccount == null || rawPendingAccount.isBlank()) {
                break;
            }
            PendingAccount pendingAccount = parsePendingAccount(rawPendingAccount);
            if (pendingAccount == null) {
                continue;
            }
            futures.add(applyExecutor(workerCount).submit(
                    () -> processOnePendingApplyAccount(pendingAccount, statementLimit)));
        }
        int processed = 0;
        for (Future<Integer> future : futures) {
            try {
                processed += future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // Continue with other accounts.
            }
        }
        return processed;
    }

    private void enqueuePendingApply(String userId, String currency, Long createTime) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return;
        }
        long monthStart = resolveMonthRange(resolveCreateTime(createTime))[0];
        String monthCode = formatMonthCode(monthStart);
        String monthSetKey = buildPendingApplyMonthSetKey(userId, currency);
        redisUtil.addSetMember(monthSetKey, monthCode);
        redisUtil.expire(monthSetKey, SNAPSHOT_MONTH_SET_EXPIRE_SECONDS);
        redisUtil.addSetMember(PENDING_BALANCE_APPLY_SET_KEY, userId + "|" + currency);
    }

    private int processOnePendingApplyAccount(PendingAccount pendingAccount, int statementLimit) {
        List<String> pendingMonths = loadPendingMonths(buildPendingApplyMonthSetKey(
                pendingAccount.userId(), pendingAccount.currency()));
        if (pendingMonths.isEmpty()) {
            AccountStatementsDto anchor = accountStatementsMapper.selectEarliestPendingApplyAnchor(
                    pendingAccount.userId(), pendingAccount.currency());
            if (anchor == null || anchor.getCreateTime() == null) {
                log.info("account statement apply no anchor, account={}", pendingAccount.rawKey());
                return 0;
            }
            enqueuePendingApply(pendingAccount.userId(), pendingAccount.currency(), anchor.getCreateTime());
            pendingMonths = loadPendingMonths(buildPendingApplyMonthSetKey(
                    pendingAccount.userId(), pendingAccount.currency()));
            if (pendingMonths.isEmpty()) {
                log.warn("account statement apply anchor reseed empty, account={}, anchorId={}",
                        pendingAccount.rawKey(), anchor.getId());
                return 0;
            }
            log.info("account statement apply anchor reseeded, account={}, anchorId={}, month={}",
                    pendingAccount.rawKey(), anchor.getId(), pendingMonths.get(0));
        }
        for (String monthCode : pendingMonths) {
            int updated = processOnePendingApplyMonth(pendingAccount, statementLimit, monthCode);
            if (updated > 0) {
                return updated;
            }
        }
        return 0;
    }

    private int processOnePendingApplyMonth(PendingAccount pendingAccount, int statementLimit, String monthCode) {
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
            removePendingMonth(buildPendingApplyMonthSetKey(pendingAccount.userId(), pendingAccount.currency()), monthCode);
            log.info("account statement apply month empty, account={}, month={}, table={}",
                    pendingAccount.rawKey(), monthCode, partitionTable);
            return 0;
        }
        boolean hasMoreInMonth = pendingStatements.size() > statementLimit;
        List<AccountStatementsDto> statementsToProcess = hasMoreInMonth
                ? new ArrayList<>(pendingStatements.subList(0, statementLimit))
                : pendingStatements;
        int updated = applyBalanceForStatements(statementsToProcess);
        Long firstId = statementsToProcess.get(0).getId();
        Long lastId = statementsToProcess.get(statementsToProcess.size() - 1).getId();
        log.info("account statement apply batch, account={}, month={}, table={}, fetched={}, processing={}, updated={}, hasMore={}, firstId={}, lastId={}",
                pendingAccount.rawKey(), monthCode, partitionTable, pendingStatements.size(), statementsToProcess.size(),
                updated, hasMoreInMonth, firstId, lastId);
        if (hasMoreInMonth) {
            redisUtil.addSetMember(PENDING_BALANCE_APPLY_SET_KEY, pendingAccount.rawKey());
            return updated;
        }
        removePendingMonth(buildPendingApplyMonthSetKey(pendingAccount.userId(), pendingAccount.currency()), monthCode);
        if (!loadPendingMonths(buildPendingApplyMonthSetKey(pendingAccount.userId(), pendingAccount.currency())).isEmpty()) {
            redisUtil.addSetMember(PENDING_BALANCE_APPLY_SET_KEY, pendingAccount.rawKey());
        } else if (!loadPendingMonths(buildPendingSnapshotMonthSetKey(pendingAccount.userId(), pendingAccount.currency())).isEmpty()) {
            redisUtil.addSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY, pendingAccount.rawKey());
        }
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
        if (orderType == null || (orderType != CommonConstant.STATEMENT_ORDER_TYPE_COLLECTION
                && orderType != CommonConstant.STATEMENT_ORDER_TYPE_PAYOUT)) {
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
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return;
        }
        long monthStart = resolveMonthRange(resolveCreateTime(createTime))[0];
        String monthCode = formatMonthCode(monthStart);
        String monthSetKey = buildPendingSnapshotMonthSetKey(userId, currency);
        redisUtil.addSetMember(monthSetKey, monthCode);
        redisUtil.expire(monthSetKey, SNAPSHOT_MONTH_SET_EXPIRE_SECONDS);
        redisUtil.addSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY, userId + "|" + currency);
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

    private void removePendingMonth(String monthSetKey, String monthCode) {
        redisUtil.removeSetMember(monthSetKey, monthCode);
        if (!redisUtil.getSetMembers(monthSetKey).isEmpty()) {
            redisUtil.expire(monthSetKey, SNAPSHOT_MONTH_SET_EXPIRE_SECONDS);
        }
    }

    private String buildPendingApplyMonthSetKey(String userId, String currency) {
        return PENDING_BALANCE_APPLY_MONTH_SET_KEY_PREFIX + userId + "|" + currency;
    }

    private String buildPendingSnapshotMonthSetKey(String userId, String currency) {
        return PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX + userId + "|" + currency;
    }

    private long resolveCreateTime(Long createTime) {
        if (createTime != null && createTime > 0) {
            return createTime;
        }
        return System.currentTimeMillis() / 1000;
    }

    private String formatMonthCode(long monthStartEpochSecond) {
        LocalDate monthStart = Instant.ofEpochSecond(monthStartEpochSecond).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format("%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
    }

    private long parseMonthCodeToEpochSecond(String monthCode) {
        int year = Integer.parseInt(monthCode.substring(0, 4));
        int month = Integer.parseInt(monthCode.substring(4, 6));
        return LocalDate.of(year, month, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    private long[] resolveMonthRange(long createTime) {
        LocalDate monthStart = Instant.ofEpochSecond(createTime)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        return new long[]{
                monthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
                nextMonthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        };
    }

    private String resolvePartitionTable(long createTime) {
        long[] range = resolveMonthRange(createTime);
        LocalDate monthStart = Instant.ofEpochSecond(range[0]).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format("account_statements_%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
    }

    private PendingAccount parsePendingAccount(String rawPendingAccount) {
        if (rawPendingAccount == null || rawPendingAccount.isBlank() || !rawPendingAccount.contains("|")) {
            return null;
        }
        String[] parts = rawPendingAccount.split("\\|", 2);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return new PendingAccount(parts[0], parts[1], rawPendingAccount);
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

    private record PendingAccount(String userId, String currency, String rawKey) {
    }
}
