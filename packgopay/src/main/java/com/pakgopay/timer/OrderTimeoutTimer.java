package com.pakgopay.timer;

import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PayOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class OrderTimeoutTimer {

    private static final String LOCK_KEY = "job:order_timeout:lock";
    private static final int LOCK_SECONDS = 50;
    private static final long TIMEOUT_SECONDS = 10 * 60L;
    private static final long SCAN_RECENT_SECONDS = 2 * 24 * 60 * 60L;
    private static final String TIMEOUT_REMARK = "timeout_no_notify_10m";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runOnce("startup");
    }

    @Scheduled(cron = "0 0 */6 * * ?")
    public void runEverySixHours() {
        runOnce("scheduled");
    }

    private void runOnce(String source) {
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockVal, Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("order timeout skipped, lock held by other instance, source={}", source);
            return;
        }
        try {
            long now = System.currentTimeMillis() / 1000;
            long minTime = now - SCAN_RECENT_SECONDS;
            long deadline = now - TIMEOUT_SECONDS;

            int collectionUpdated = collectionOrderMapper.markTimeoutOrders(
                    String.valueOf(TransactionStatus.PROCESSING.getCode()),
                    String.valueOf(TransactionStatus.EXPIRED.getCode()),
                    minTime,
                    deadline,
                    now,
                    TIMEOUT_REMARK);

            int payoutUpdated = payOrderMapper.markTimeoutOrders(
                    String.valueOf(TransactionStatus.PROCESSING.getCode()),
                    String.valueOf(TransactionStatus.EXPIRED.getCode()),
                    minTime,
                    deadline,
                    now,
                    TIMEOUT_REMARK);

            if (collectionUpdated > 0 || payoutUpdated > 0) {
                log.warn("order timeout handled, source={}, minTime={}, deadline={}, collectionUpdated={}, payoutUpdated={}",
                        source, minTime, deadline, collectionUpdated, payoutUpdated);
            } else {
                log.info("order timeout checked, source={}, minTime={}, deadline={}, no expired orders",
                        source, minTime, deadline);
            }
        } catch (Exception e) {
            log.error("order timeout task failed, source={}, message={}", source, e.getMessage());
        } finally {
            String currentVal = redisTemplate.opsForValue().get(LOCK_KEY);
            if (lockVal.equals(currentVal)) {
                redisTemplate.delete(LOCK_KEY);
            }
        }
    }
}
