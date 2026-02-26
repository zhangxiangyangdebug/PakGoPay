package com.pakgopay.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class SnowflakeIdService {

    private static final int MAX_WORKER_ID = 1023;
    private static final int LEASE_SECONDS = 30;
    private static final int HEARTBEAT_MILLIS = 10000;
    private static final String REDIS_PREFIX = "snowflake:worker";
    private static final String APP_NAME = "packgopay";
    private static final String SERVER_PORT = "8090";

    private final StringRedisTemplate redisTemplate;
    private final String instanceToken;

    private volatile long currentWorkerId = -1L;
    private volatile SnowflakeIdGenerator generator;

    public SnowflakeIdService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.instanceToken = buildInstanceToken(APP_NAME, SERVER_PORT);
    }

    @PostConstruct
    public void init() {
        allocateWorkerIdOrThrow();
    }

    public long nextId() {
        SnowflakeIdGenerator local = generator;
        if (local == null) {
            synchronized (this) {
                if (generator == null) {
                    allocateWorkerIdOrThrow();
                }
                local = generator;
            }
        }
        return local.nextId();
    }

    public String nextId(String prefix) {
        return prefix + nextId();
    }

    @Scheduled(fixedDelay = HEARTBEAT_MILLIS)
    public void heartbeat() {
        long workerId = currentWorkerId;
        if (workerId < 0) {
            return;
        }

        String key = workerKey(workerId);
        try {
            String currentValue = redisTemplate.opsForValue().get(key);
            if (instanceToken.equals(currentValue)) {
                redisTemplate.expire(key, Duration.ofSeconds(LEASE_SECONDS));
                return;
            }
            log.warn("snowflake worker lease lost, workerId={}, expected={}, actual={}",
                    workerId, instanceToken, currentValue);
        } catch (Exception e) {
            log.error("snowflake worker heartbeat failed, workerId={}, message={}",
                    workerId, e.getMessage());
            return;
        }

        synchronized (this) {
            if (currentWorkerId == workerId) {
                allocateWorkerIdOrThrow();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        long workerId = currentWorkerId;
        if (workerId < 0) {
            return;
        }

        String key = workerKey(workerId);
        DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('DEL', KEYS[1]) else return 0 end");
        releaseScript.setResultType(Long.class);

        try {
            redisTemplate.execute(releaseScript, Collections.singletonList(key), instanceToken);
            log.info("snowflake worker released, workerId={}, key={}", workerId, key);
        } catch (Exception e) {
            log.warn("snowflake worker release failed, workerId={}, message={}", workerId, e.getMessage());
        }
    }

    private synchronized void allocateWorkerIdOrThrow() {
        int upperBound = Math.max(MAX_WORKER_ID, 0);
        int start = ThreadLocalRandom.current().nextInt(upperBound + 1);
        for (int i = 0; i <= upperBound; i++) {
            int candidate = (start + i) % (upperBound + 1);
            String key = workerKey(candidate);
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        key, instanceToken, Duration.ofSeconds(LEASE_SECONDS));
                if (Boolean.TRUE.equals(acquired)) {
                    currentWorkerId = candidate;
                    generator = new SnowflakeIdGenerator(candidate);
                    log.info("snowflake worker allocated, workerId={}, key={}", candidate, key);
                    return;
                }
            } catch (Exception e) {
                log.warn("snowflake worker candidate failed, key={}, message={}", key, e.getMessage());
            }
        }
        throw new IllegalStateException("No available snowflake workerId in Redis");
    }

    private String workerKey(long workerId) {
        return REDIS_PREFIX + ":" + workerId;
    }

    private String buildInstanceToken(String appName, String serverPort) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        return appName + ":" + host + ":" + serverPort + ":" + pid + ":" + UUID.randomUUID();
    }
}
