package com.pakgopay.timer;

import com.pakgopay.service.ChannelPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class CounterFlushTimer {

    private static final String FLUSH_LOCK_KEY = "job:counter_flush:lock";
    private static final int FLUSH_LOCK_SECONDS = 5;
    private static final String RECONCILE_LOCK_KEY = "job:collection_limit_reconcile:lock";
    private static final int RECONCILE_LOCK_SECONDS = 30;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChannelPaymentService channelPaymentService;

    @Scheduled(fixedDelayString = "${pakgopay.counter.flush-interval-ms:2000}", initialDelayString = "${pakgopay.counter.flush-initial-delay-ms:5000}")
    public void flushCounterDeltas() {
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(FLUSH_LOCK_KEY, lockVal, Duration.ofSeconds(FLUSH_LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            channelPaymentService.flushCounterDeltas();
        } catch (Exception e) {
            log.warn("counter flush task failed, message={}", e.getMessage());
        } finally {
            String current = stringRedisTemplate.opsForValue().get(FLUSH_LOCK_KEY);
            if (lockVal.equals(current)) {
                stringRedisTemplate.delete(FLUSH_LOCK_KEY);
            }
        }
    }

    @Scheduled(fixedDelayString = "${pakgopay.counter.reconcile-interval-ms:60000}", initialDelayString = "${pakgopay.counter.reconcile-initial-delay-ms:10000}")
    public void reconcileCollectionAmountUsage() {
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RECONCILE_LOCK_KEY, lockVal, Duration.ofSeconds(RECONCILE_LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            channelPaymentService.reconcileCollectionAmountUsage();
        } catch (Exception e) {
            log.warn("collection limit reconcile task failed, message={}", e.getMessage());
        } finally {
            String current = stringRedisTemplate.opsForValue().get(RECONCILE_LOCK_KEY);
            if (lockVal.equals(current)) {
                stringRedisTemplate.delete(RECONCILE_LOCK_KEY);
            }
        }
    }
}
