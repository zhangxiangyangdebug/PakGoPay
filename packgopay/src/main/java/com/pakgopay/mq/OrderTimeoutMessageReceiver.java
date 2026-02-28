package com.pakgopay.mq;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.entity.transaction.OrderTimeoutMessage;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OrderTimeoutMessageReceiver {

    private static final String TIMEOUT_REMARK = "timeout_no_notify_10m";

    private final CollectionOrderMapper collectionOrderMapper;
    private final PayOrderMapper payOrderMapper;

    public OrderTimeoutMessageReceiver(
            CollectionOrderMapper collectionOrderMapper,
            PayOrderMapper payOrderMapper) {
        this.collectionOrderMapper = collectionOrderMapper;
        this.payOrderMapper = payOrderMapper;
    }

    @RabbitListener(queues = {
            RabbitConfig.ORDER_TIMEOUT_COLLECTION_QUEUE,
            RabbitConfig.ORDER_TIMEOUT_PAYING_QUEUE
    })
    public void onMessage(Message message) {
        String queue = message.getMessageProperties().getConsumerQueue();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            OrderTimeoutMessage timeoutMessage = JSON.parseObject(body, OrderTimeoutMessage.class);
            if (timeoutMessage == null
                    || timeoutMessage.getTransactionNo() == null
                    || timeoutMessage.getTransactionNo().isBlank()) {
                log.warn("order timeout mq skip invalid payload, queue={}, body={}", queue, body);
                return;
            }

            long now = System.currentTimeMillis() / 1000;
            long[] range = resolveTransactionNoTimeRange(timeoutMessage.getTransactionNo());
            if (RabbitConfig.ORDER_TIMEOUT_COLLECTION_QUEUE.equals(queue)) {
                CollectionOrderDto update = new CollectionOrderDto();
                update.setTransactionNo(timeoutMessage.getTransactionNo());
                update.setOrderStatus(String.valueOf(TransactionStatus.EXPIRED.getCode()));
                update.setUpdateTime(now);
                update.setRemark(TIMEOUT_REMARK);
                int updated = collectionOrderMapper.updateByTransactionNoWhenProcessing(
                        update,
                        String.valueOf(TransactionStatus.PROCESSING.getCode()),
                        range[0],
                        range[1]);
                log.info("order timeout mq handled, type=collection, transactionNo={}, updated={}",
                        timeoutMessage.getTransactionNo(), updated);
                return;
            }

            if (RabbitConfig.ORDER_TIMEOUT_PAYING_QUEUE.equals(queue)) {
                PayOrderDto update = new PayOrderDto();
                update.setTransactionNo(timeoutMessage.getTransactionNo());
                update.setOrderStatus(String.valueOf(TransactionStatus.EXPIRED.getCode()));
                update.setUpdateTime(now);
                update.setRemark(TIMEOUT_REMARK);
                int updated = payOrderMapper.updateByTransactionNoWhenProcessing(
                        update,
                        String.valueOf(TransactionStatus.PROCESSING.getCode()),
                        range[0],
                        range[1]);
                log.info("order timeout mq handled, type=payout, transactionNo={}, updated={}",
                        timeoutMessage.getTransactionNo(), updated);
                return;
            }

            log.warn("order timeout mq skip unknown queue, queue={}, body={}", queue, body);
        } catch (Exception e) {
            log.error("order timeout mq handle failed, queue={}, body={}, message={}", queue, body, e.getMessage());
        }
    }

    private long[] resolveTransactionNoTimeRange(String transactionNo) {
        long[] range = SnowflakeIdGenerator.extractMonthEpochSecondRange(transactionNo);
        if (range == null) {
            return new long[]{0L, Long.MAX_VALUE};
        }
        return range;
    }
}
