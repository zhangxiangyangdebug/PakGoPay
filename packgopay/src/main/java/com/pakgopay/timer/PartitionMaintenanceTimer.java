package com.pakgopay.timer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Slf4j
@Component
public class PartitionMaintenanceTimer {

    private static final String LOCK_KEY = "job:partition_maintenance:lock";
    private static final int LOCK_SECONDS = 600;
    private static final int FUTURE_MONTHS_TO_PRECREATE = 2;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runOnce("startup");
    }

    // every day 01:10
    @Scheduled(cron = "0 10 1 * * ?")
    public void runDaily() {
        runOnce("daily");
    }

    private void runOnce(String source) {
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockVal, Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("partition maintenance skipped, lock held by other instance, source={}", source);
            return;
        }
        try {
            LocalDate firstDayOfCurrentMonth = LocalDate.now(ZoneOffset.UTC)
                    .with(TemporalAdjusters.firstDayOfMonth());
            for (int i = 0; i <= FUTURE_MONTHS_TO_PRECREATE; i++) {
                LocalDate monthStart = firstDayOfCurrentMonth.plusMonths(i);
                LocalDate monthEnd = monthStart.plusMonths(1);
                ensureMonthlyPartition("collection_order", monthStart, monthEnd);
                ensureMonthlyPartition("pay_order", monthStart, monthEnd);
            }
            log.info("partition maintenance done, source={}", source);
        } catch (Exception e) {
            log.error("partition maintenance failed, source={}, message={}", source, e.getMessage());
        } finally {
            String currentVal = redisTemplate.opsForValue().get(LOCK_KEY);
            if (lockVal.equals(currentVal)) {
                redisTemplate.delete(LOCK_KEY);
            }
        }
    }

    private void ensureMonthlyPartition(String parentTable, LocalDate monthStart, LocalDate monthEnd) {
        long fromEpoch = monthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long toEpoch = monthEnd.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        String suffix = String.format("%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
        String partTable = parentTable + "_" + suffix;

        String ddl = String.format(
                "create table if not exists public.%s partition of public.%s for values from (%d) to (%d)",
                partTable, parentTable, fromEpoch, toEpoch);
        jdbcTemplate.execute(ddl);

        ensurePartitionIndexes(partTable);
        log.info("partition ready, parent={}, partition={}, from={}, to={}",
                parentTable, partTable, fromEpoch, toEpoch);
    }

    private void ensurePartitionIndexes(String partTable) {
        // local unique indexes (partition-level uniqueness)
        jdbcTemplate.execute(String.format(
                "create unique index if not exists uk_%s_txn on public.%s (transaction_no)",
                partTable, partTable));
        jdbcTemplate.execute(String.format(
                "create unique index if not exists uk_%s_mch_ord on public.%s (merchant_user_id, merchant_order_no)",
                partTable, partTable));

        // query indexes
        jdbcTemplate.execute(String.format(
                "create index if not exists idx_%s_mch_ctime on public.%s (merchant_user_id, create_time desc)",
                partTable, partTable));
        jdbcTemplate.execute(String.format(
                "create index if not exists idx_%s_payment_ctime on public.%s (payment_id, create_time)",
                partTable, partTable));
        jdbcTemplate.execute(String.format(
                "create index if not exists idx_%s_currency_ctime on public.%s (currency_type, create_time)",
                partTable, partTable));
        jdbcTemplate.execute(String.format(
                "create index if not exists idx_%s_mch_curr_ctime on public.%s (merchant_user_id, currency_type, create_time)",
                partTable, partTable));
    }
}
