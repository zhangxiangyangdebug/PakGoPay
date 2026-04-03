package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.data.response.OrderFlowLogQueryResponse;
import com.pakgopay.mapper.secondary.CollectionOrderFlowLogMapper;
import com.pakgopay.mapper.secondary.PayOrderFlowLogMapper;
import com.pakgopay.mapper.dto.OrderFlowLogDto;
import com.pakgopay.service.common.OrderFlowLogService;
import com.pakgopay.service.common.OrderFlowLogEvent;
import com.pakgopay.service.common.OrderFlowLogSession;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.SensitiveDataMaskUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrderFlowLogServiceImpl implements OrderFlowLogService {

    private static final DateTimeFormatter MONTH_PARTITION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    // Async queue config.
    private static final int QUEUE_CAPACITY = 200000;

    // Keys that must be masked in flow log payload.
    private static final Set<String> FLOW_LOG_SENSITIVE_KEYS = Set.of("apikey", "signkey");

    @Autowired
    private CollectionOrderFlowLogMapper collectionOrderFlowLogMapper;

    @Autowired
    private PayOrderFlowLogMapper payOrderFlowLogMapper;

    @Value("${pakgopay.order-flow-log.enabled:true}")
    private boolean enabled;

    @Value("${pakgopay.order-flow-log.batch-size:500}")
    private int batchSize;

    @Value("${pakgopay.order-flow-log.max-batch-rounds-per-flush:20}")
    private int maxBatchRoundsPerFlush;

    @Value("${pakgopay.order-flow-log.queue-monitor-every-n-flush:100}")
    private int queueMonitorEveryNFlush;

    @Value("${pakgopay.order-flow-log.worker-count:8}")
    private int workerCount;

    @Value("${pakgopay.order-flow-log.max-inflight-batches:16}")
    private int maxInflightBatches;

    @Value("${pakgopay.order-flow-log.insert-slow-ms:200}")
    private long insertSlowMs;

    private final LinkedBlockingQueue<QueueEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private long flushInvocationCount = 0;
    private ExecutorService flushExecutor;
    private Semaphore inFlightBatchSemaphore;
    private static final OrderFlowLogSession NO_OP_SESSION = new OrderFlowLogSession() {
        @Override
        public void add(OrderFlowStepEnum step, Boolean success, Object payload) {
        }

        @Override
        public void flush() {
        }
    };

    @PostConstruct
    public void initAsyncFlushWorkers() {
        int resolvedWorkerCount = Math.max(1, workerCount);
        int resolvedMaxInflightBatches = Math.max(resolvedWorkerCount, maxInflightBatches);
        AtomicInteger workerIndex = new AtomicInteger(1);
        this.flushExecutor = Executors.newFixedThreadPool(resolvedWorkerCount, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("order-flow-log-flush-" + workerIndex.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        this.inFlightBatchSemaphore = new Semaphore(resolvedMaxInflightBatches);
    }

    /**
     * Create a session for collection order flow logs.
     */
    @Override
    public OrderFlowLogSession newCollectionSession(String transactionNo) {
        if (!enabled) {
            return NO_OP_SESSION;
        }
        return new DefaultOrderFlowLogSession(transactionNo, true);
    }

    /**
     * Create a session for payout order flow logs.
     */
    @Override
    public OrderFlowLogSession newPayoutSession(String transactionNo) {
        if (!enabled) {
            return NO_OP_SESSION;
        }
        return new DefaultOrderFlowLogSession(transactionNo, false);
    }

    /**
     * Enqueue a collection flow event for async persistence.
     */
    @Override
    public void logCollection(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        if (!enabled) {
            return;
        }
        enqueue(transactionNo, step, success, payload, true);
    }

    /**
     * Enqueue a payout flow event for async persistence.
     */
    @Override
    public void logPayout(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        if (!enabled) {
            return;
        }
        enqueue(transactionNo, step, success, payload, false);
    }

    /**
     * Enqueue multiple flow events in one service call.
     */
    @Override
    public void logBatch(String transactionNo, boolean collection, List<OrderFlowLogEvent> events) {
        if (!enabled) {
            return;
        }
        if (events == null || events.isEmpty()) {
            return;
        }
        for (OrderFlowLogEvent event : events) {
            if (event == null) {
                continue;
            }
            enqueue(transactionNo, event.getStep(), event.getSuccess(), event.getPayload(), collection);
        }
    }

    /**
     * Query flow logs by transaction number.
     * Sort order: stepSeq, eventTime, createTime, id.
     */
    @Override
    public OrderFlowLogQueryResponse listByTransactionNo(String transactionNo) {
        OrderFlowLogQueryResponse response = new OrderFlowLogQueryResponse();
        response.setTransactionNo(transactionNo);
        response.setFlowLogs(listLogsByTransactionNo(transactionNo));
        return response;
    }

    /**
     * Scheduled async flush.
     */
    @Scheduled(
            fixedDelayString = "${pakgopay.order-flow-log.flush-delay-ms:50}",
            initialDelayString = "${pakgopay.order-flow-log.initial-delay-ms:1000}"
    )
    public void flushAsyncQueue() {
        if (!enabled) {
            return;
        }
        flushInvocationCount++;
        int queueSize = queue.size();
        if (queueSize > 0
                && queueMonitorEveryNFlush > 0
                && flushInvocationCount % queueMonitorEveryNFlush == 0) {
            log.info("order flow log queue stats, size={}, remainingCapacity={}, capacity={}, batchSize={}, maxBatchRoundsPerFlush={}",
                    queueSize,
                    queue.remainingCapacity(),
                    QUEUE_CAPACITY,
                    batchSize,
                    maxBatchRoundsPerFlush);
        }
        flushQueueBatches();
    }

    /**
     * Flush all remaining events before bean shutdown.
     */
    @PreDestroy
    public void flushOnShutdown() {
        if (!enabled) {
            return;
        }
        while (!queue.isEmpty()) {
            flushQueueBatch();
        }
        if (flushExecutor != null) {
            flushExecutor.shutdown();
            try {
                if (!flushExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    flushExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flushExecutor.shutdownNow();
            }
        }
    }

    /**
     * Load flow logs from matching order table(s) using month boundary from transactionNo.
     */
    private List<OrderFlowLogDto> listLogsByTransactionNo(String transactionNo) {
        if (transactionNo == null || transactionNo.isBlank()) {
            return Collections.emptyList();
        }
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        long startTime = range[0];
        long endTime = range[1];
        if (CommonUtil.isCollectionTransactionNo(transactionNo)) {
            return collectionOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime);
        }
        if (CommonUtil.isPayoutTransactionNo(transactionNo)) {
            return payOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime);
        }

        // Fallback: unknown prefix, query both tables then merge-sort.
        List<OrderFlowLogDto> result = new ArrayList<>();
        result.addAll(collectionOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime));
        result.addAll(payOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime));
        result.sort(Comparator.comparing(OrderFlowLogDto::getStepSeq, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(OrderFlowLogDto::getEventTime, Comparator.nullsLast(Long::compareTo))
                .thenComparing(OrderFlowLogDto::getCreateTime, Comparator.nullsLast(Long::compareTo))
                .thenComparing(OrderFlowLogDto::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    /**
     * Put one event into async queue.
     */
    private void enqueue(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload, boolean collection) {
        if (transactionNo == null || transactionNo.isBlank() || step == null) {
            return;
        }
        long createTime = resolveCreateTimeFromTransactionNo(transactionNo);
        long eventTime = System.currentTimeMillis() / 1000;
        QueueEvent event = new QueueEvent(
                transactionNo,
                step,
                success,
                payload,
                collection,
                MDC.get("traceId"),
                eventTime,
                createTime
        );
        boolean offered = queue.offer(event);
        if (!offered) {
            log.warn("order flow log queue full, drop event, transactionNo={}, step={}", transactionNo, step.getCode());
        }
    }

    /**
     * Flush up to configured rounds each schedule tick.
     */
    private void flushQueueBatches() {
        for (int i = 0; i < maxBatchRoundsPerFlush; i++) {
            if (queue.isEmpty()) {
                return;
            }
            flushQueueBatch();
        }
    }

    /**
     * Drain one batch from queue, then bulk insert into target tables.
     */
    private void flushQueueBatch() {
        if (queue.isEmpty() || flushExecutor == null || inFlightBatchSemaphore == null) {
            return;
        }
        if (!inFlightBatchSemaphore.tryAcquire()) {
            return;
        }

        List<QueueEvent> drained = new ArrayList<>(batchSize);
        queue.drainTo(drained, batchSize);
        if (drained.isEmpty()) {
            inFlightBatchSemaphore.release();
            return;
        }

        flushExecutor.execute(() -> {
            try {
                List<OrderFlowLogDto> collectionList = new ArrayList<>();
                List<OrderFlowLogDto> payoutList = new ArrayList<>();
                for (QueueEvent event : drained) {
                    OrderFlowLogDto dto = buildLogDto(
                            event.transactionNo(),
                            event.step(),
                            event.success(),
                            event.payload(),
                            event.traceId(),
                            event.eventTime(),
                            event.createTime()
                    );
                    if (event.collection()) {
                        collectionList.add(dto);
                    } else {
                        payoutList.add(dto);
                    }
                }

                saveBatchWithRetry(collectionList, true);
                saveBatchWithRetry(payoutList, false);
            } catch (Exception e) {
                log.error("order flow async flush worker failed, batchSize={}, message={}",
                        drained.size(), e.getMessage(), e);
            } finally {
                inFlightBatchSemaphore.release();
            }
        });
    }

    /**
     * Batch insert with one retry to reduce transient DB failures.
     */
    private void saveBatchWithRetry(List<OrderFlowLogDto> list, boolean collection) {
        if (list == null || list.isEmpty()) {
            return;
        }
        try {
            insertBatch(list, collection);
        } catch (Exception first) {
            log.warn("order flow async batch insert failed, retrying once, collection={}, size={}, message={}",
                    collection, list.size(), first.getMessage());
            try {
                insertBatch(list, collection);
            } catch (Exception second) {
                log.error("order flow async batch insert failed after retry, collection={}, size={}, message={}",
                        collection, list.size(), second.getMessage());
            }
        }
    }

    /**
     * Insert batch to collection/payout table by route flag.
     */
    private void insertBatch(List<OrderFlowLogDto> list, boolean collection) {
        long start = System.currentTimeMillis();
        try {
            Map<String, List<OrderFlowLogDto>> partitionedBatches = splitByMonthlyPartition(list, collection);
            for (Map.Entry<String, List<OrderFlowLogDto>> entry : partitionedBatches.entrySet()) {
                if (collection) {
                    collectionOrderFlowLogMapper.insertBatch(entry.getKey(), entry.getValue());
                } else {
                    payOrderFlowLogMapper.insertBatch(entry.getKey(), entry.getValue());
                }
            }
        } finally {
            long costMs = System.currentTimeMillis() - start;
            if (costMs >= insertSlowMs) {
                log.info("order flow batch insert cost, collection={}, size={}, costMs={}, worker={}",
                        collection, list.size(), costMs, Thread.currentThread().getName());
            }
        }
    }

    private Map<String, List<OrderFlowLogDto>> splitByMonthlyPartition(List<OrderFlowLogDto> list, boolean collection) {
        Map<String, List<OrderFlowLogDto>> grouped = new LinkedHashMap<>();
        String parentTable = collection ? "collection_order_flow_log" : "pay_order_flow_log";
        for (OrderFlowLogDto dto : list) {
            String tableName = parentTable + "_" + resolvePartitionMonthSuffix(dto.getCreateTime());
            grouped.computeIfAbsent(tableName, key -> new ArrayList<>()).add(dto);
        }
        return grouped;
    }

    private String resolvePartitionMonthSuffix(Long createTime) {
        long epochSecond = createTime == null || createTime <= 0
                ? System.currentTimeMillis() / 1000
                : createTime;
        return MONTH_PARTITION_FORMATTER.format(Instant.ofEpochSecond(epochSecond));
    }

    /**
     * Build one DB dto from event fields.
     */
    private OrderFlowLogDto buildLogDto(
            String transactionNo,
            OrderFlowStepEnum step,
            Boolean success,
            Object payload,
            String traceId,
            long eventTime,
            long createTime) {
        OrderFlowLogDto dto = new OrderFlowLogDto();
        dto.setTransactionNo(transactionNo);
        dto.setStepCode(step.getCode());
        dto.setStepName(step.getName());
        dto.setStepSeq(step.getSeq());
        dto.setSuccess(success);
        dto.setPayload(serializePayloadSafely(payload));
        dto.setTraceId(traceId);
        dto.setEventTime(eventTime);
        dto.setCreateTime(createTime);
        return dto;
    }

    /**
     * Resolve createTime from transactionNo snowflake timestamp.
     * If failed, fallback to current epoch second.
     */
    private long resolveCreateTimeFromTransactionNo(String transactionNo) {
        Long epochSecond = SnowflakeIdGenerator.extractEpochSecondFromPrefixedId(transactionNo);
        if (epochSecond == null || epochSecond <= 0) {
            long fallback = System.currentTimeMillis() / 1000;
            log.warn("resolve flow log createTime fallback now, transactionNo={}, now={}", transactionNo, fallback);
            return fallback;
        }
        return epochSecond;
    }

    /**
     * Serialize payload after masking sensitive keys.
     */
    private String serializePayloadSafely(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            Object sanitized = SensitiveDataMaskUtil.sanitizePayload(payload, FLOW_LOG_SENSITIVE_KEYS);
            return JSON.toJSONString(sanitized);
        } catch (Exception e) {
            // Keep logging non-blocking even if payload masking fails.
            log.warn("serializePayloadSafely failed, fallback raw json, message={}", e.getMessage());
            return JSON.toJSONString(payload);
        }
    }

    /**
     * Session-based flow recorder.
     * Used by create-order path where multiple steps are flushed together.
     */
    private class DefaultOrderFlowLogSession implements OrderFlowLogSession {
        private final String transactionNo;
        private final boolean collection;
        private final String traceId;
        private final List<FlowEvent> events = new ArrayList<>();
        private boolean flushed = false;

        private DefaultOrderFlowLogSession(String transactionNo, boolean collection) {
            this.transactionNo = transactionNo;
            this.collection = collection;
            this.traceId = MDC.get("traceId");
        }

        /**
         * Add one event into local session buffer.
         */
        @Override
        public void add(OrderFlowStepEnum step, Boolean success, Object payload) {
            if (flushed || step == null) {
                return;
            }
            events.add(new FlowEvent(step, success, payload, System.currentTimeMillis() / 1000));
        }

        /**
         * Flush buffered events by batch insert.
         */
        @Override
        public void flush() {
            if (flushed) {
                return;
            }
            flushed = true;
            if (events.isEmpty()) {
                return;
            }

            long createTime = resolveCreateTimeFromTransactionNo(transactionNo);
            for (FlowEvent event : events) {
                boolean offered = queue.offer(new QueueEvent(
                        transactionNo,
                        event.step(),
                        event.success(),
                        event.payload(),
                        collection,
                        traceId,
                        event.eventTime(),
                        createTime
                ));
                if (!offered) {
                    log.warn("order flow log queue full, drop flush event, transactionNo={}, step={}",
                            transactionNo, event.step() == null ? null : event.step().getCode());
                }
            }
            events.clear();
        }
    }

    private record FlowEvent(OrderFlowStepEnum step, Boolean success, Object payload, long eventTime) {
    }

    private record QueueEvent(
            String transactionNo,
            OrderFlowStepEnum step,
            Boolean success,
            Object payload,
            boolean collection,
            String traceId,
            long eventTime,
            long createTime) {
    }
}
