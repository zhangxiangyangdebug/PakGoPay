package com.pakgopay.timer;

import com.pakgopay.service.common.AccountStatementApplyService;
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
public class AccountStatementApplyTimer {

    private static final String LOCK_KEY = "job:account_statement_apply:lock";

    @Autowired
    private AccountStatementApplyService accountStatementApplyService;

    @Autowired
    private RedissonClient redissonClient;

    @Value("${pakgopay.account-statement.apply-account-limit:40}")
    private int accountLimit;

    @Value("${pakgopay.account-statement.apply-statement-limit:150}")
    private int statementLimit;

    @Scheduled(
            fixedDelayString = "${pakgopay.account-statement.apply-interval-ms:1500}",
            initialDelayString = "${pakgopay.account-statement.apply-initial-delay-ms:5000}")
    public void processPendingApplies() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("account statement apply interrupted while acquiring lock");
            return;
        }
        if (!locked) {
            return;
        }
        try {
            int processed = accountStatementApplyService.processPendingApplies(accountLimit, statementLimit);
            if (processed > 0) {
                log.info("account statement apply done, processed={}", processed);
            }
        } catch (Exception e) {
            log.warn("account statement apply failed, message={}", e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
