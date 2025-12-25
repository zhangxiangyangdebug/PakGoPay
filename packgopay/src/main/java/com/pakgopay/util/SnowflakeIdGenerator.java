package com.pakgopay.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class SnowflakeIdGenerator {

    private static final long WORKER_ID = 10;

    // ====== 位数分配 ======
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS  = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);   // 1023
    private static final long MAX_SEQUENCE  = ~(-1L << SEQUENCE_BITS);    // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;            // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 22

    // ====== 自定义纪元（建议固定在项目启动后不要变）======
    // 例如：2024-01-01 00:00:00 UTC
    private static final long EPOCH = 1704067200000L;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    public SnowflakeIdGenerator() {
        this.workerId = WORKER_ID;
    }

    /** 生成下一个ID（线程安全） */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 处理时钟回拨：now < lastTimestamp
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            // 小回拨：等待追平
            if (offset <= 5) {
                timestamp = waitUntil(lastTimestamp);
            } else {
                // 大回拨：直接报错（生产更安全）或降级策略
                throw new IllegalStateException("Clock moved backwards. Refusing for " + offset + "ms");
            }
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内：序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号溢出：等到下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒：序列号重置（可用随机数减少热点）
            sequence = ThreadLocalRandom.current().nextLong(0, 2); // 0或1
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /** 反解析：从雪花ID提取毫秒时间戳 */
    public static String extractTimeMillis(long id) {
        long delta = id >>> TIMESTAMP_SHIFT;
        long snowTime = delta + EPOCH;
        Instant instant = Instant.ofEpochMilli(snowTime);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"));
        String formattedDate = zonedDateTime.format(formatter);
        return formattedDate;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long waitUntil(long targetTs) {
        long ts = currentTimeMillis();
        while (ts < targetTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }

    public static void main(String[] args) throws InterruptedException {
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(WORKER_ID);
        long id = idGenerator.nextId();
        System.out.println(id);
        System.out.println(idGenerator.extractTimeMillis(id));

    }
}
