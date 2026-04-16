package com.pakgopay.service.impl;

import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.timer.PartitionMaintenanceTimer;
import com.pakgopay.util.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;

import java.math.BigDecimal;
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
public abstract class AbstractStatementSnapshotService {

    public static final String PENDING_BALANCE_SNAPSHOT_SET_KEY = "job:account_statement:pending_balance_snapshot";
    private static final String PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX = "job:account_statement:pending_balance_snapshot:months:";
    private static final int SNAPSHOT_UPDATE_BATCH_SIZE = 50;
    private static final int SNAPSHOT_MONTH_SET_EXPIRE_SECONDS = 86400;
    private static final int SNAPSHOT_LOOKBACK_MONTHS = 12;

    @Autowired
    protected AccountStatementsMapper accountStatementsMapper;

    @Autowired
    protected RedisUtil redisUtil;

    @Value("${pakgopay.account-statement.snapshot-worker-count:8}")
    private int snapshotWorkerCount;

    private volatile ExecutorService snapshotExecutor;

    /**
     * Add one user+currency task into the deduplicated pending snapshot set.
     * Month partition is resolved later from the earliest pending row so one account never fans out into multiple hot keys.
     */
    public void enqueuePendingSnapshot(String userId, String currency, Long createTime) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return;
        }
        long monthStart = resolveMonthRange(resolveSnapshotCreateTime(createTime))[0];
        String monthCode = formatMonthCode(monthStart);
        String monthSetKey = buildPendingSnapshotMonthSetKey(userId, currency);
        redisUtil.addSetMember(monthSetKey, monthCode);
        redisUtil.expire(monthSetKey, SNAPSHOT_MONTH_SET_EXPIRE_SECONDS);
        redisUtil.addSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY, userId + "|" + currency);
    }

    /**
     * Consume up to {@code accountLimit} accounts this round, and process up to
     * {@code statementLimit} statement rows per account.
     */
    public int processPendingSnapshots(int accountLimit, int statementLimit) {
        int workerCount = Math.max(1, snapshotWorkerCount);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < accountLimit; i++) {
            // Use pop-and-process to avoid repeatedly favoring the same accounts.
            String rawPendingAccount = redisUtil.popSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY);
            if (rawPendingAccount == null || rawPendingAccount.isBlank()) {
                break;
            }
            PendingAccount pendingAccount = parsePendingAccount(rawPendingAccount);
            if (pendingAccount == null) {
                continue;
            }
            futures.add(snapshotExecutor(workerCount).submit(
                    () -> processOnePendingAccount(pendingAccount, statementLimit)));
        }
        int processed = 0;
        for (Future<Integer> future : futures) {
            try {
                processed += future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // Continue with other accounts; one bad account should not block the whole round.
            }
        }
        return processed;
    }

    private int processOnePendingAccount(PendingAccount pendingAccount, int statementLimit) {
        List<String> pendingMonths = loadPendingSnapshotMonths(pendingAccount.userId(), pendingAccount.currency());
        if (pendingMonths.isEmpty()) {
            AccountStatementsDto anchor = accountStatementsMapper.selectEarliestPendingSnapshotAnchor(
                    pendingAccount.userId(),
                    pendingAccount.currency());
            if (anchor == null || anchor.getCreateTime() == null) {
                log.info("account statement snapshot no anchor, account={}", pendingAccount.rawKey());
                return 0;
            }
            enqueuePendingSnapshot(pendingAccount.userId(), pendingAccount.currency(), anchor.getCreateTime());
            pendingMonths = loadPendingSnapshotMonths(pendingAccount.userId(), pendingAccount.currency());
            if (pendingMonths.isEmpty()) {
                log.warn("account statement snapshot anchor reseed empty, account={}, anchorId={}",
                        pendingAccount.rawKey(), anchor.getId());
                return 0;
            }
            log.info("account statement snapshot anchor reseeded, account={}, anchorId={}, month={}",
                    pendingAccount.rawKey(), anchor.getId(), pendingMonths.get(0));
        }
        for (String monthCode : pendingMonths) {
            int updated = processOnePendingAccountMonth(pendingAccount, statementLimit, monthCode);
            if (updated > 0) {
                return updated;
            }
        }
        return 0;
    }

    private int processOnePendingAccountMonth(PendingAccount pendingAccount,
                                              int statementLimit,
                                              String monthCode) {
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
            removePendingSnapshotMonth(pendingAccount.userId(), pendingAccount.currency(), monthCode);
            log.info("account statement snapshot month empty, account={}, month={}, table={}",
                    pendingAccount.rawKey(), monthCode, partitionTable);
            return 0;
        }
        boolean hasMoreInMonth = pendingStatements.size() > statementLimit;
        List<AccountStatementsDto> statementsToProcess = hasMoreInMonth
                ? new ArrayList<>(pendingStatements.subList(0, statementLimit))
                : pendingStatements;

        // write before/after data
        int updated = applySnapshotsForAccount(
                pendingAccount.userId(),
                pendingAccount.currency(),
                monthRange,
                statementsToProcess);
        Long firstId = statementsToProcess.get(0).getId();
        Long lastId = statementsToProcess.get(statementsToProcess.size() - 1).getId();
        log.info("account statement snapshot batch, account={}, month={}, table={}, fetched={}, processing={}, updated={}, hasMore={}, firstId={}, lastId={}",
                pendingAccount.rawKey(), monthCode, partitionTable, pendingStatements.size(), statementsToProcess.size(),
                updated, hasMoreInMonth, firstId, lastId);
        if (hasMoreInMonth) {
            redisUtil.addSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY, pendingAccount.rawKey());
            return updated;
        }
        removePendingSnapshotMonth(pendingAccount.userId(), pendingAccount.currency(), monthCode);
        if (!loadPendingSnapshotMonths(pendingAccount.userId(), pendingAccount.currency()).isEmpty()) {
            redisUtil.addSetMember(PENDING_BALANCE_SNAPSHOT_SET_KEY, pendingAccount.rawKey());
        }
        return updated;
    }

    /**
     * Backfill one ordered batch for the same user+currency using the latest completed row as baseline.
     * Ordering is defined by the database auto-increment id, which matches statement persistence order.
     */
    private int applySnapshotsForAccount(String userId,
                                         String currency,
                                         long[] currentMonthRange,
                                         List<AccountStatementsDto> pendingStatements) {
        AccountStatementsDto latestCompleted = findLatestCompletedBefore(
                userId,
                currency,
                pendingStatements.get(0).getId(),
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

    /**
     * Search the latest completed baseline from the current pending month backwards month by month.
     */
    private AccountStatementsDto findLatestCompletedBefore(String userId,
                                                           String currency,
                                                           Long id,
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
                        userId, currency, id, probeMonthStart);
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
                    userId, currency, id, probeMonthStart);
            if (latestCompleted != null) {
                return latestCompleted;
            }
            probeMonthStart = previousMonthStart(probeMonthStart);
        }
        return null;
    }

    /**
     * Convert the Redis member format {@code userId|currency} into a small typed object.
     */
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

    private List<String> loadPendingSnapshotMonths(String userId, String currency) {
        return loadPendingMonths(buildPendingSnapshotMonthSetKey(userId, currency));
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
                                                                   Long id,
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
                    id);
        } catch (BadSqlGrammarException e) {
            String message = e.getMessage();
            if (message != null && message.contains("does not exist")) {
                log.info("account statement snapshot baseline partition missing, table={}", partitionTable);
                return null;
            }
            throw e;
        }
    }

    private void removePendingSnapshotMonth(String userId, String currency, String monthCode) {
        String monthSetKey = buildPendingSnapshotMonthSetKey(userId, currency);
        removePendingMonth(monthSetKey, monthCode);
    }

    private void removePendingMonth(String monthSetKey, String monthCode) {
        redisUtil.removeSetMember(monthSetKey, monthCode);
        if (!redisUtil.getSetMembers(monthSetKey).isEmpty()) {
            redisUtil.expire(monthSetKey, SNAPSHOT_MONTH_SET_EXPIRE_SECONDS);
        }
    }

    private String buildPendingSnapshotMonthSetKey(String userId, String currency) {
        return PENDING_BALANCE_SNAPSHOT_MONTH_SET_KEY_PREFIX + userId + "|" + currency;
    }

    private long resolveSnapshotCreateTime(Long createTime) {
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
        if (monthCode == null || monthCode.length() != 6) {
            throw new IllegalArgumentException("invalid monthCode: " + monthCode);
        }
        int year = Integer.parseInt(monthCode.substring(0, 4));
        int month = Integer.parseInt(monthCode.substring(4, 6));
        return LocalDate.of(year, month, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
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
            case 1 -> new SnapshotDelta(amount, BigDecimal.ZERO, amount);
            case 2 -> new SnapshotDelta(BigDecimal.ZERO, amount.negate(), amount.negate());
            case 3 -> new SnapshotDelta(amount, BigDecimal.ZERO, amount);
            default -> null;
        };
    }

    protected record SnapshotDelta(BigDecimal availableDelta, BigDecimal frozenDelta, BigDecimal totalDelta) {
    }

    private record PendingAccount(String userId, String currency, String rawKey) {
    }

    /**
     * Resolve the month partition range [start, end) from one statement create_time.
     */
    protected long[] resolveMonthRange(long createTime) {
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

    protected long previousMonthStart(long monthStartEpochSecond) {
        LocalDate previousMonth = Instant.ofEpochSecond(monthStartEpochSecond)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .minusMonths(1);
        return previousMonth.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    protected long nextMonthStart(long monthStartEpochSecond) {
        LocalDate nextMonth = Instant.ofEpochSecond(monthStartEpochSecond)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1);
        return nextMonth.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    protected String resolvePartitionTable(long createTime) {
        long[] range = resolveMonthRange(createTime);
        LocalDate monthStart = Instant.ofEpochSecond(range[0]).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format("account_statements_%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
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

    protected long resolveCreateTimeFromSerialNo(String serialNo) {
        Long epochSecond = SnowflakeIdGenerator.extractEpochSecondFromPrefixedId(serialNo);
        if (epochSecond != null && epochSecond > 0) {
            return epochSecond;
        }
        return System.currentTimeMillis() / 1000;
    }
}
