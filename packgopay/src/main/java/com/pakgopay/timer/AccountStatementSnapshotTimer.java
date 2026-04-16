package com.pakgopay.timer;

import com.pakgopay.service.common.AccountStatementService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AccountStatementSnapshotTimer {

    private static final String LOCK_KEY = "job:account_statement_snapshot:lock";

    @Autowired
    private AccountStatementService accountStatementService;

    @Autowired
    private RedissonClient redissonClient;

    @Value("${pakgopay.account-statement.snapshot-account-limit:40}")
    private int accountLimit;

    @Value("${pakgopay.account-statement.snapshot-statement-limit:150}")
    private int statementLimit;

    /**
     * Run one global snapshot round. The timer lock guarantees there is only one cluster-wide
     * producer round at a time, while the service uses local worker threads for cross-account parallelism.
     */
    @Scheduled(
            fixedDelayString = "${pakgopay.account-statement.snapshot-interval-ms:2000}",
            initialDelayString = "${pakgopay.account-statement.snapshot-initial-delay-ms:8000}")
    public void processPendingSnapshots() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("account statement snapshot interrupted while acquiring lock");
            return;
        }
        if (!locked) {
            return;
        }
        try {
            int processed = accountStatementService.processPendingSnapshots(accountLimit, statementLimit);
            if (processed > 0) {
                log.info("account statement snapshot done, processed={}", processed);
            }
        } catch (Exception e) {
            log.warn("account statement snapshot failed, message={}", e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
