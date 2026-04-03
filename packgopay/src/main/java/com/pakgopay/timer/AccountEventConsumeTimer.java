package com.pakgopay.timer;

import com.pakgopay.service.common.AccountEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class AccountEventConsumeTimer {

    private static final String LOCK_KEY = "job:account_event_consume:lock";
    private static final int LOCK_SECONDS = 10;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AccountEventService accountEventService;

    /**
     * Consume account events with distributed lock protection:
     * - fixed delay keeps backpressure stable
     * - each run processes multiple small rounds
     */
    @Scheduled(
            fixedDelayString = "${pakgopay.account-event.consume-interval-ms:2000}",
            initialDelayString = "${pakgopay.account-event.consume-initial-delay-ms:5000}")
    public void consume() {
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockVal, Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            // One tick consumes up to 3*300 rows (best effort).
            int consumed = accountEventService.consumePendingEvents(
                    300,
                    3);
            if (consumed > 0) {
                log.info("account event consume done, consumed={}", consumed);
            }
        } catch (Exception e) {
            log.warn("account event consume failed, message={}", e.getMessage());
        } finally {
            String current = stringRedisTemplate.opsForValue().get(LOCK_KEY);
            if (lockVal.equals(current)) {
                stringRedisTemplate.delete(LOCK_KEY);
            }
        }
    }
}
