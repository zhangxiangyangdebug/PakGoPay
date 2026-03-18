package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.data.response.OrderFlowLogQueryResponse;
import com.pakgopay.mapper.CollectionOrderFlowLogMapper;
import com.pakgopay.mapper.PayOrderFlowLogMapper;
import com.pakgopay.mapper.dto.OrderFlowLogDto;
import com.pakgopay.service.common.OrderFlowLogService;
import com.pakgopay.service.common.OrderFlowLogSession;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.SensitiveDataMaskUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class OrderFlowLogServiceImpl implements OrderFlowLogService {

    // Async queue config.
    private static final int QUEUE_CAPACITY = 20000;
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCH_ROUNDS_PER_FLUSH = 2;

    // Keys that must be masked in flow log payload.
    private static final Set<String> FLOW_LOG_SENSITIVE_KEYS = Set.of("apikey", "signkey");

    @Autowired
    private CollectionOrderFlowLogMapper collectionOrderFlowLogMapper;

    @Autowired
    private PayOrderFlowLogMapper payOrderFlowLogMapper;

    private final LinkedBlockingQueue<QueueEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /**
     * Create a session for collection order flow logs.
     */
    @Override
    public OrderFlowLogSession newCollectionSession(String transactionNo) {
        return new DefaultOrderFlowLogSession(transactionNo, true);
    }

    /**
     * Create a session for payout order flow logs.
     */
    @Override
    public OrderFlowLogSession newPayoutSession(String transactionNo) {
        return new DefaultOrderFlowLogSession(transactionNo, false);
    }

    /**
     * Enqueue a collection flow event for async persistence.
     */
    @Override
    public void logCollection(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        enqueue(transactionNo, step, success, payload, true);
    }

    /**
     * Enqueue a payout flow event for async persistence.
     */
    @Override
    public void logPayout(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        enqueue(transactionNo, step, success, payload, false);
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
    @Scheduled(fixedDelay = 200, initialDelay = 1000)
    public void flushAsyncQueue() {
        flushQueueBatches();
    }

    /**
     * Flush all remaining events before bean shutdown.
     */
    @PreDestroy
    public void flushOnShutdown() {
        while (!queue.isEmpty()) {
            flushQueueBatch();
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
        for (int i = 0; i < MAX_BATCH_ROUNDS_PER_FLUSH; i++) {
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
        if (queue.isEmpty()) {
            return;
        }

        List<QueueEvent> drained = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(drained, BATCH_SIZE);
        if (drained.isEmpty()) {
            return;
        }

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
        if (collection) {
            collectionOrderFlowLogMapper.insertBatch(list);
        } else {
            payOrderFlowLogMapper.insertBatch(list);
        }
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
            List<OrderFlowLogDto> dtoList = new ArrayList<>(events.size());
            for (FlowEvent event : events) {
                dtoList.add(buildLogDto(
                        transactionNo,
                        event.step(),
                        event.success(),
                        event.payload(),
                        traceId,
                        event.eventTime(),
                        createTime
                ));
            }

            try {
                insertBatch(dtoList, collection);
            } catch (Exception e) {
                log.warn("order flow log batch insert failed, transactionNo={}, message={}",
                        transactionNo, e.getMessage());
            } finally {
                events.clear();
            }
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
