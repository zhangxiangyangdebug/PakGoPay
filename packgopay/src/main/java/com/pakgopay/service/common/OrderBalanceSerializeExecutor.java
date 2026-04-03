package com.pakgopay.service.common;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class OrderBalanceSerializeExecutor {

    private static final int LOCK_WAIT_MILLIS = 5000;
    private static final int LOCK_SLEEP_MILLIS = 30;
    private static final int LOCK_EXPIRE_SECONDS = 15;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void run(String key, Runnable action) {
        if (action == null) {
            return;
        }
        if (key == null || key.isBlank()) {
            action.run();
            return;
        }
        String lockKey = "lock:order_balance:" + key;
        String lockValue = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + LOCK_WAIT_MILLIS;
        while (true) {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    action.run();
                    return;
                } finally {
                    String current = stringRedisTemplate.opsForValue().get(lockKey);
                    if (lockValue.equals(current)) {
                        stringRedisTemplate.delete(lockKey);
                    }
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new PakGoPayException(ResultCode.FAIL, "acquire order balance serialize lock timeout");
            }
            try {
                Thread.sleep(LOCK_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PakGoPayException(ResultCode.FAIL, "acquire order balance serialize lock interrupted");
            }
        }
    }
}
