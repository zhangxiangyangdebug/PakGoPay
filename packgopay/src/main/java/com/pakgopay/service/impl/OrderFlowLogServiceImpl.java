package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.data.response.OrderFlowLogQueryResponse;
import com.pakgopay.mapper.CollectionOrderFlowLogMapper;
import com.pakgopay.mapper.PayOrderFlowLogMapper;
import com.pakgopay.mapper.dto.OrderFlowLogDto;
import com.pakgopay.service.common.OrderFlowLogService;
import com.pakgopay.service.common.OrderFlowLogSession;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class OrderFlowLogServiceImpl implements OrderFlowLogService {

    private static final int QUEUE_CAPACITY = 20000;
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCH_ROUNDS_PER_FLUSH = 2;

    @Autowired
    private CollectionOrderFlowLogMapper collectionOrderFlowLogMapper;

    @Autowired
    private PayOrderFlowLogMapper payOrderFlowLogMapper;

    private final LinkedBlockingQueue<QueueEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Override
    public OrderFlowLogSession newCollectionSession(String transactionNo) {
        return new DefaultOrderFlowLogSession(transactionNo, true);
    }

    @Override
    public OrderFlowLogSession newPayoutSession(String transactionNo) {
        return new DefaultOrderFlowLogSession(transactionNo, false);
    }

    @Override
    public void logCollection(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        enqueue(transactionNo, step, success, payload, true);
    }

    @Override
    public void logPayout(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload) {
        enqueue(transactionNo, step, success, payload, false);
    }

    @Override
    public OrderFlowLogQueryResponse listByTransactionNo(String transactionNo) {
        OrderFlowLogQueryResponse response = new OrderFlowLogQueryResponse();
        response.setTransactionNo(transactionNo);
        response.setFlowLogs(listLogsByTransactionNo(transactionNo));
        return response;
    }

    private List<OrderFlowLogDto> listLogsByTransactionNo(String transactionNo) {
        if (transactionNo == null || transactionNo.isBlank()) {
            return Collections.emptyList();
        }
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        long startTime = range[0];
        long endTime = range[1];
        if (transactionNo.startsWith(CommonConstant.COLLECTION_PREFIX)) {
            return collectionOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime);
        }
        if (transactionNo.startsWith(CommonConstant.PAYOUT_PREFIX)) {
            return payOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime);
        }
        List<OrderFlowLogDto> result = new ArrayList<>();
        result.addAll(collectionOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime));
        result.addAll(payOrderFlowLogMapper.listByTransactionNo(transactionNo, startTime, endTime));
        result.sort(Comparator.comparing(OrderFlowLogDto::getStepSeq, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(OrderFlowLogDto::getEventTime, Comparator.nullsLast(Long::compareTo))
                .thenComparing(OrderFlowLogDto::getCreateTime, Comparator.nullsLast(Long::compareTo))
                .thenComparing(OrderFlowLogDto::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    private void enqueue(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload, boolean collection) {
        if (transactionNo == null || transactionNo.isBlank() || step == null) {
            return;
        }
        long createTime = resolveCreateTimeFromTransactionNo(transactionNo);
        long eventTime = System.currentTimeMillis() / 1000;
        boolean offered = queue.offer(new QueueEvent(
                transactionNo,
                step,
                success,
                payload,
                collection,
                MDC.get("traceId"),
                eventTime,
                createTime));
        if (!offered) {
            log.warn("order flow log queue full, drop event, transactionNo={}, step={}", transactionNo, step.getCode());
        }
    }

    @Scheduled(fixedDelay = 200, initialDelay = 1000)
    public void flushAsyncQueue() {
        flushQueueBatches();
    }

    @PreDestroy
    public void flushOnShutdown() {
        while (!queue.isEmpty()) {
            flushQueueBatch();
        }
    }

    private void flushQueueBatches() {
        for (int i = 0; i < MAX_BATCH_ROUNDS_PER_FLUSH; i++) {
            if (queue.isEmpty()) {
                return;
            }
            flushQueueBatch();
        }
    }

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
            OrderFlowLogDto dto = new OrderFlowLogDto();
            dto.setTransactionNo(event.transactionNo());
            dto.setStepCode(event.step().getCode());
            dto.setStepName(event.step().getName());
            dto.setStepSeq(event.step().getSeq());
            dto.setSuccess(event.success());
            dto.setPayload(event.payload() == null ? null : JSON.toJSONString(event.payload()));
            dto.setTraceId(event.traceId());
            dto.setEventTime(event.eventTime());
            dto.setCreateTime(event.createTime());
            if (event.collection()) {
                collectionList.add(dto);
            } else {
                payoutList.add(dto);
            }
        }

        saveBatchWithRetry(collectionList, true);
        saveBatchWithRetry(payoutList, false);
    }

    private void saveBatchWithRetry(List<OrderFlowLogDto> list, boolean collection) {
        if (list == null || list.isEmpty()) {
            return;
        }
        try {
            if (collection) {
                collectionOrderFlowLogMapper.insertBatch(list);
            } else {
                payOrderFlowLogMapper.insertBatch(list);
            }
        } catch (Exception first) {
            log.warn("order flow async batch insert failed, retrying once, collection={}, size={}, message={}",
                    collection, list.size(), first.getMessage());
            try {
                if (collection) {
                    collectionOrderFlowLogMapper.insertBatch(list);
                } else {
                    payOrderFlowLogMapper.insertBatch(list);
                }
            } catch (Exception second) {
                log.error("order flow async batch insert failed after retry, collection={}, size={}, message={}",
                        collection, list.size(), second.getMessage());
            }
        }
    }

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

        @Override
        public void add(OrderFlowStepEnum step, Boolean success, Object payload) {
            if (flushed || step == null) {
                return;
            }
            events.add(new FlowEvent(step, success, payload, System.currentTimeMillis() / 1000));
        }

        @Override
        public void flush() {
            if (flushed) {
                return;
            }
            flushed = true;
            if (events.isEmpty()) {
                return;
            }
            List<OrderFlowLogDto> dtoList = new ArrayList<>(events.size());
            long createTime = resolveCreateTimeFromTransactionNo(transactionNo);
            for (FlowEvent event : events) {
                OrderFlowLogDto dto = new OrderFlowLogDto();
                dto.setTransactionNo(transactionNo);
                dto.setStepCode(event.step().getCode());
                dto.setStepName(event.step().getName());
                dto.setStepSeq(event.step().getSeq());
                dto.setSuccess(event.success());
                dto.setPayload(event.payload() == null ? null : JSON.toJSONString(event.payload()));
                dto.setTraceId(traceId);
                dto.setEventTime(event.eventTime());
                dto.setCreateTime(createTime);
                dtoList.add(dto);
            }
            try {
                if (collection) {
                    collectionOrderFlowLogMapper.insertBatch(dtoList);
                } else {
                    payOrderFlowLogMapper.insertBatch(dtoList);
                }
            } catch (Exception e) {
                log.warn("order flow log batch insert failed, transactionNo={}, message={}",
                        transactionNo, e.getMessage());
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

    private long resolveCreateTimeFromTransactionNo(String transactionNo) {
        Long epochSecond = SnowflakeIdGenerator.extractEpochSecondFromPrefixedId(transactionNo);
        if (epochSecond == null || epochSecond <= 0) {
            long fallback = System.currentTimeMillis() / 1000;
            log.warn("resolve flow log createTime fallback now, transactionNo={}, now={}", transactionNo, fallback);
            return fallback;
        }
        return epochSecond;
    }
}
