package com.pakgopay.timer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PartitionMaintenanceTimer {

    private static final String LOCK_KEY = "job:partition_maintenance:lock";
    public static final String ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY = "meta:account_statement:partitions";
    private static final int LOCK_SECONDS = 600;
    private static final int FUTURE_MONTHS_TO_PRECREATE = 2;
    private static final int PARTITION_CACHE_EXPIRE_SECONDS = 3 * 24 * 3600;
    private static final Pattern MONTH_PARTITION_PATTERN = Pattern.compile("^(.*)_(\\d{6})$");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private ObjectProvider<JdbcTemplate> secondaryJdbcTemplateProvider;

    @Value("${pakgopay.account-statements.partition-retain-months:6}")
    private int accountStatementsRetainMonths;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runOnce("startup");
    }

    // Run daily at 01:10
    @Scheduled(cron = "0 10 1 * * ?")
    public void runDaily() {
        runOnce("daily");
    }

    /**
     * One maintenance round:
     * 1) pre-create current+future month partitions
     * 2) clean old monthly partitions
     */
    private void runOnce(String source) {
        log.info("PartitionMaintenanceTimer runOnce start");
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, lockVal, Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("partition maintenance skipped, lock held by other instance, source={}", source);
            return;
        }

        try {
            logDataSourceContext(source);

            // Resolve once; avoid repeated provider lookup inside loop.
            JdbcTemplate secondaryJdbcTemplate = secondaryJdbcTemplateProvider.getIfAvailable();
            LocalDate firstDayOfCurrentMonth = LocalDate.now(ZoneOffset.UTC)
                    .with(TemporalAdjusters.firstDayOfMonth());

            for (int i = 0; i <= FUTURE_MONTHS_TO_PRECREATE; i++) {
                LocalDate monthStart = firstDayOfCurrentMonth.plusMonths(i);
                LocalDate monthEnd = monthStart.plusMonths(1);

                // Primary DB partition parents.
                ensureMonthlyPartitionIfParentExists(jdbcTemplate, "collection_order", monthStart, monthEnd);
                ensureMonthlyPartitionIfParentExists(jdbcTemplate, "pay_order", monthStart, monthEnd);
                ensureMonthlyPartitionIfParentExists(jdbcTemplate, "collection_order_flow_log", monthStart, monthEnd);
                ensureMonthlyPartitionIfParentExists(jdbcTemplate, "pay_order_flow_log", monthStart, monthEnd);
                ensureMonthlyPartitionIfParentExists(jdbcTemplate, "account_statements", monthStart, monthEnd);

                // Secondary DB partition parents.
                if (secondaryJdbcTemplate != null) {
                    ensureMonthlyPartitionIfParentExists(secondaryJdbcTemplate, "balance_change_log", monthStart, monthEnd);
                    ensureMonthlyPartitionIfParentExists(secondaryJdbcTemplate, "collection_order_flow_log", monthStart, monthEnd);
                    ensureMonthlyPartitionIfParentExists(secondaryJdbcTemplate, "pay_order_flow_log", monthStart, monthEnd);
                } else {
                    log.info("secondary datasource unavailable, skip parents=balance_change_log,collection_order_flow_log,pay_order_flow_log");
                }
            }

            cleanupOldMonthlyPartitionsIfParentExists(
                    jdbcTemplate,
                    "account_statements",
                    Math.max(accountStatementsRetainMonths, 1));
            refreshAccountStatementPartitionCache();

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

    private void logDataSourceContext(String source) {
        try {
            String database = jdbcTemplate.queryForObject("select current_database()", String.class);
            String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            String searchPath = jdbcTemplate.queryForObject("select current_setting('search_path')", String.class);
            String collectionOrder = jdbcTemplate.queryForObject(
                    "select to_regclass('public.collection_order')", String.class);
            String payOrder = jdbcTemplate.queryForObject(
                    "select to_regclass('public.pay_order')", String.class);
            log.info(
                    "partition datasource context, source={}, database={}, schema={}, searchPath={}, public.collection_order={}, public.pay_order={}",
                    source, database, schema, searchPath, collectionOrder, payOrder);
        } catch (Exception e) {
            log.error("partition datasource context failed, source={}, message={}", source, e.getMessage());
        }
    }

    /**
     * Create one month partition [monthStart, monthEnd) and required indexes.
     */
    private void ensureMonthlyPartition(
            JdbcTemplate targetJdbcTemplate, String parentTable, LocalDate monthStart, LocalDate monthEnd) {
        long fromEpoch = monthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long toEpoch = monthEnd.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        String suffix = String.format("%04d%02d", monthStart.getYear(), monthStart.getMonthValue());
        String partTable = parentTable + "_" + suffix;

        String ddl = String.format(
                "create table if not exists public.%s partition of public.%s for values from (%d) to (%d)",
                partTable, parentTable, fromEpoch, toEpoch);
        targetJdbcTemplate.execute(ddl);

        ensurePartitionIndexes(targetJdbcTemplate, parentTable, partTable);
        log.info("partition ready, parent={}, partition={}, from={}, to={}",
                parentTable, partTable, fromEpoch, toEpoch);
    }

    /**
     * Run partition create only when parent exists and is partitioned.
     */
    private void ensureMonthlyPartitionIfParentExists(
            JdbcTemplate targetJdbcTemplate, String parentTable, LocalDate monthStart, LocalDate monthEnd) {
        String regClass = targetJdbcTemplate.queryForObject(
                String.format("select to_regclass('public.%s')", parentTable), String.class);
        if (regClass == null || regClass.isBlank()) {
            log.info("partition parent missing, skip parent={}", parentTable);
            return;
        }
        if (!isPartitionedParent(targetJdbcTemplate, parentTable)) {
            log.info("partition parent is not partitioned, skip parent={}", parentTable);
            return;
        }
        ensureMonthlyPartition(targetJdbcTemplate, parentTable, monthStart, monthEnd);
    }

    /**
     * Create partition-local indexes based on table type.
     */
    private void ensurePartitionIndexes(JdbcTemplate targetJdbcTemplate, String parentTable, String partTable) {
        if ("collection_order_flow_log".equals(parentTable) || "pay_order_flow_log".equals(parentTable)) {
            targetJdbcTemplate.execute(String.format(
                    "drop index if exists public.idx_%s_txn_seq_etime",
                    partTable));
            targetJdbcTemplate.execute(String.format(
                    "drop index if exists public.idx_%s_txn_seq_ctime",
                    partTable));
            targetJdbcTemplate.execute(String.format(
                    "drop index if exists public.idx_%s_ctime",
                    partTable));
            targetJdbcTemplate.execute(String.format(
                    "drop index if exists public.idx_%s_step_ctime",
                    partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_txn_ctime on public.%s (transaction_no, create_time)",
                    partTable, partTable));
            return;
        }

        if ("collection_order".equals(parentTable) || "pay_order".equals(parentTable)) {
            // Local uniqueness for each month partition.
            targetJdbcTemplate.execute(String.format(
                    "create unique index if not exists uk_%s_txn on public.%s (transaction_no)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create unique index if not exists uk_%s_mch_ord on public.%s (merchant_user_id, merchant_order_no)",
                    partTable, partTable));

            // Common query indexes.
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_mch_ctime on public.%s (merchant_user_id, create_time desc)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_payment_ctime on public.%s (payment_id, create_time)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_currency_ctime on public.%s (currency_type, create_time)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_mch_curr_ctime on public.%s (merchant_user_id, currency_type, create_time)",
                    partTable, partTable));
            return;
        }

        if ("balance_change_log".equals(parentTable)) {
            targetJdbcTemplate.execute(String.format(
                    "create unique index if not exists uk_%s_biz on public.%s (biz_type, biz_no)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_mch_curr_ctime on public.%s (merchant_user_id, currency, created_at)",
                    partTable, partTable));
            return;
        }

        if ("account_statements".equals(parentTable)) {
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_txn_ctime on public.%s (transaction_no, create_time desc)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_user_ctime on public.%s (user_id, create_time desc)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_user_role_ctime on public.%s (user_role, create_time desc)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_currency_ctime on public.%s (currency, create_time desc)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create unique index if not exists uk_%s_serial_no on public.%s (serial_no)",
                    partTable, partTable));
            targetJdbcTemplate.execute(String.format(
                    "create index if not exists idx_%s_status_user_curr_ctime on public.%s (status, user_id, currency, create_time, id)",
                    partTable, partTable));
        }
    }

    /**
     * Check if public.parentTable is partitioned parent.
     */
    private boolean isPartitionedParent(JdbcTemplate targetJdbcTemplate, String parentTable) {
        Boolean isPartitioned = targetJdbcTemplate.queryForObject(
                "select exists (" +
                        "select 1 " +
                        "from pg_partitioned_table pt " +
                        "join pg_class c on c.oid = pt.partrelid " +
                        "join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and c.relname = ?" +
                        ")",
                Boolean.class,
                parentTable);
        return Boolean.TRUE.equals(isPartitioned);
    }

    /**
     * Drop old month partitions by suffix convention: parent_YYYYMM.
     */
    private void cleanupOldMonthlyPartitionsIfParentExists(
            JdbcTemplate targetJdbcTemplate, String parentTable, int retainMonths) {
        String regClass = targetJdbcTemplate.queryForObject(
                String.format("select to_regclass('public.%s')", parentTable), String.class);
        if (regClass == null || regClass.isBlank()) {
            log.info("partition cleanup skip, parent missing, parent={}", parentTable);
            return;
        }
        if (!isPartitionedParent(targetJdbcTemplate, parentTable)) {
            log.info("partition cleanup skip, parent is not partitioned, parent={}", parentTable);
            return;
        }

        int cutoffYm = toYearMonth(LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .minusMonths(retainMonths));
        List<String> partitions = targetJdbcTemplate.queryForList(
                "select child.relname " +
                        "from pg_inherits inh " +
                        "join pg_class parent on parent.oid = inh.inhparent " +
                        "join pg_namespace pn on pn.oid = parent.relnamespace " +
                        "join pg_class child on child.oid = inh.inhrelid " +
                        "join pg_namespace cn on cn.oid = child.relnamespace " +
                        "where pn.nspname = 'public' " +
                        "  and cn.nspname = 'public' " +
                        "  and parent.relname = ?",
                String.class,
                parentTable);

        for (String partName : partitions) {
            Integer ym = parsePartitionYearMonth(partName, parentTable);
            if (ym == null || ym >= cutoffYm) {
                continue;
            }
            String dropDdl = String.format("drop table if exists public.%s", partName);
            targetJdbcTemplate.execute(dropDdl);
            log.info("old partition dropped, parent={}, partition={}, cutoffYm={}",
                    parentTable, partName, cutoffYm);
        }
    }

    /**
     * Parse YYYYMM from partition table name.
     */
    private Integer parsePartitionYearMonth(String partName, String parentTable) {
        if (partName == null) {
            return null;
        }
        Matcher matcher = MONTH_PARTITION_PATTERN.matcher(partName);
        if (!matcher.matches()) {
            return null;
        }
        if (!parentTable.equals(matcher.group(1))) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toYearMonth(LocalDate date) {
        return date.getYear() * 100 + date.getMonthValue();
    }

    private void refreshAccountStatementPartitionCache() {
        String regClass = jdbcTemplate.queryForObject(
                "select to_regclass('public.account_statements')", String.class);
        if (regClass == null || regClass.isBlank() || !isPartitionedParent(jdbcTemplate, "account_statements")) {
            redisTemplate.delete(ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY);
            log.info("account statement partition cache cleared, reason=parent_missing_or_not_partitioned");
            return;
        }

        List<String> partitions = jdbcTemplate.queryForList(
                "select child.relname " +
                        "from pg_inherits inh " +
                        "join pg_class parent on parent.oid = inh.inhparent " +
                        "join pg_namespace pn on pn.oid = parent.relnamespace " +
                        "join pg_class child on child.oid = inh.inhrelid " +
                        "join pg_namespace cn on cn.oid = child.relnamespace " +
                        "where pn.nspname = 'public' " +
                        "  and cn.nspname = 'public' " +
                        "  and parent.relname = ?",
                String.class,
                "account_statements");

        redisTemplate.delete(ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY);
        for (String partName : partitions) {
            Integer ym = parsePartitionYearMonth(partName, "account_statements");
            if (ym != null) {
                redisTemplate.opsForSet().add(
                        ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY,
                        String.format("%06d", ym));
            }
        }
        redisTemplate.expire(ACCOUNT_STATEMENT_PARTITION_MONTH_SET_KEY, Duration.ofSeconds(PARTITION_CACHE_EXPIRE_SECONDS));
        log.info("account statement partition cache refreshed, size={}",
                partitions == null ? 0 : partitions.size());
    }
}
