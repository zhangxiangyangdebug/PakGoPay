package com.pakgopay.service.impl;

import com.pakgopay.mapper.AccountStatementTaskCursorMapper;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.dto.AccountStatementTaskCursorDto;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.thirdUtil.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public abstract class AbstractAccountStatementTaskSupport {

    protected static final int TASK_MONTH_SET_EXPIRE_SECONDS = 86400;

    @Autowired
    protected AccountStatementsMapper accountStatementsMapper;

    @Autowired
    protected AccountStatementTaskCursorMapper accountStatementTaskCursorMapper;

    @Autowired
    protected RedisUtil redisUtil;

    protected abstract String taskType();

    protected abstract String taskSetKey();

    protected abstract String taskMonthSetKeyPrefix();

    protected int processPendingTaskCursors(int accountLimit,
                                            ExecutorService executor,
                                            BiFunction<PendingAccount, AccountStatementTaskCursorDto, Integer> accountProcessor) {
        List<Future<Integer>> futures = new ArrayList<>();
        List<AccountStatementTaskCursorDto> cursors = accountStatementTaskCursorMapper.listCursorsByTaskType(
                taskType(), accountLimit);
        for (AccountStatementTaskCursorDto cursor : cursors) {
            PendingAccount pendingAccount = new PendingAccount(cursor.getUserId(), cursor.getCurrency(),
                    cursor.getUserId() + "|" + cursor.getCurrency());
            futures.add(executor.submit(() -> accountProcessor.apply(pendingAccount, cursor)));
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

    protected void enqueuePendingTask(String userId,
                                      String currency,
                                      Long createTime) {
        enqueuePendingTask(userId, currency, createTime, taskType(), taskMonthSetKeyPrefix());
    }

    protected void enqueuePendingTask(String userId,
                                      String currency,
                                      Long createTime,
                                      String taskType,
                                      String monthSetKeyPrefix) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return;
        }
        long monthStart = resolveMonthRange(resolveCreateTime(createTime))[0];
        String monthCode = formatMonthCode(monthStart);
        upsertTaskCursor(userId, currency, taskType, monthCode, null);
        publishPendingTaskHint(userId, currency, monthCode, taskType, monthSetKeyPrefix);
    }

    protected String resolveOrReseedMonthCode(PendingAccount pendingAccount,
                                              String currentMonthCode,
                                              Function<PendingAccount, AccountStatementsDto> anchorLoader) {
        if (currentMonthCode != null && !currentMonthCode.isBlank()) {
            return currentMonthCode;
        }
        AccountStatementsDto anchor = anchorLoader.apply(pendingAccount);
        if (anchor == null || anchor.getCreateTime() == null) {
            upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), null, null);
            redisUtil.removeSetMember(taskSetKey(), pendingAccount.rawKey());
            return null;
        }
        String monthCode = formatMonthCode(resolveMonthRange(resolveCreateTime(anchor.getCreateTime()))[0]);
        upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), monthCode, null);
        publishPendingTaskHint(pendingAccount.userId(), pendingAccount.currency(), monthCode, taskType(), null);
        return monthCode;
    }

    protected void refreshCursorFromDb(PendingAccount pendingAccount,
                                       Long lastDoneSeq,
                                       Function<PendingAccount, AccountStatementsDto> anchorLoader) {
        AccountStatementsDto anchor = anchorLoader.apply(pendingAccount);
        if (anchor == null || anchor.getCreateTime() == null) {
            upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), null, lastDoneSeq);
            redisUtil.removeSetMember(taskSetKey(), pendingAccount.rawKey());
            return;
        }
        String monthCode = formatMonthCode(resolveMonthRange(resolveCreateTime(anchor.getCreateTime()))[0]);
        upsertTaskCursor(pendingAccount.userId(), pendingAccount.currency(), taskType(), monthCode, lastDoneSeq);
        publishPendingTaskHint(pendingAccount.userId(), pendingAccount.currency(), monthCode, taskType(), null);
    }

    protected void upsertTaskCursor(String userId,
                                    String currency,
                                    String taskType,
                                    String pendingMonth,
                                    Long lastDoneSeq) {
        AccountStatementTaskCursorDto cursor = new AccountStatementTaskCursorDto();
        cursor.setUserId(userId);
        cursor.setCurrency(currency);
        cursor.setTaskType(taskType);
        cursor.setPendingMonth(pendingMonth);
        cursor.setLastDoneSeq(lastDoneSeq);
        cursor.setUpdatedTime(System.currentTimeMillis() / 1000);
        accountStatementTaskCursorMapper.upsertCursor(cursor);
    }

    protected String buildTaskMonthSetKey(String monthSetKeyPrefix, String userId, String currency) {
        return monthSetKeyPrefix + userId + "|" + currency;
    }

    protected void publishPendingTaskHint(String userId,
                                          String currency,
                                          String monthCode,
                                          String taskType,
                                          String monthSetKeyPrefix) {
        Runnable task = () -> {
            if (monthSetKeyPrefix != null && !monthSetKeyPrefix.isBlank()) {
                String monthSetKey = buildTaskMonthSetKey(monthSetKeyPrefix, userId, currency);
                redisUtil.addSetMember(monthSetKey, monthCode);
                redisUtil.expire(monthSetKey, TASK_MONTH_SET_EXPIRE_SECONDS);
            }
            redisUtil.addSetMember(resolveTaskSetKey(taskType), userId + "|" + currency);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private String resolveTaskSetKey(String taskType) {
        if (taskType().equals(taskType)) {
            return taskSetKey();
        }
        if ("snapshot".equals(taskType)) {
            return AbstractStatementSnapshotService.PENDING_BALANCE_SNAPSHOT_SET_KEY;
        }
        throw new IllegalArgumentException("unsupported taskType: " + taskType);
    }

    protected String formatMonthCode(long monthStartEpochSecond) {
        LocalDate monthStart = Instant.ofEpochSecond(monthStartEpochSecond).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format("%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
    }

    protected long parseMonthCodeToEpochSecond(String monthCode) {
        if (monthCode == null || monthCode.length() != 6) {
            throw new IllegalArgumentException("invalid monthCode: " + monthCode);
        }
        int year = Integer.parseInt(monthCode.substring(0, 4));
        int month = Integer.parseInt(monthCode.substring(4, 6));
        return LocalDate.of(year, month, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

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

    protected long resolveCreateTime(Long createTime) {
        if (createTime != null && createTime > 0) {
            return createTime;
        }
        return System.currentTimeMillis() / 1000;
    }

    protected record PendingAccount(String userId, String currency, String rawKey) {
    }
}
