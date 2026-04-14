package com.pakgopay.timer;

import com.pakgopay.service.common.AccountEventService;
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
public class AccountEventConsumeTimer {

    private static final String LOCK_KEY = "job:account_event_consume:lock";

    @Autowired
    private AccountEventService accountEventService;

    @Autowired
    private RedissonClient redissonClient;

    @Value("${pakgopay.account-event.claim-limit:200}")
    private int claimLimit;

    @Value("${pakgopay.account-event.max-rounds:3}")
    private int maxRounds;

    /**
     * Consume account events with distributed lock protection:
     * - fixed delay keeps backpressure stable
     * - each run processes multiple small rounds
     */
    @Scheduled(
            fixedDelayString = "${pakgopay.account-event.consume-interval-ms:2000}",
            initialDelayString = "${pakgopay.account-event.consume-initial-delay-ms:5000}")
    public void consume() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("account event consume interrupted while acquiring lock");
            return;
        }
        if (!locked) {
            log.debug("account event consume skipped, lock not acquired, lockKey={}", LOCK_KEY);
            return;
        }
        try {
            int consumed = accountEventService.consumePendingEvents(
                    claimLimit,
                    maxRounds);
            if (consumed > 0) {
                log.info("account event consume done, consumed={}", consumed);
            }
        } catch (Exception e) {
            log.warn("account event consume failed, message={}", e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
