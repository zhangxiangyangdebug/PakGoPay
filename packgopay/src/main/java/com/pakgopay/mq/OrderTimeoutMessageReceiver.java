package com.pakgopay.mq;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.constant.NotificationComponentType;
import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.entity.transaction.OrderTimeoutMessage;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.CollectionOrderDto;
import com.pakgopay.mapper.dto.PayOrderDto;
import com.pakgopay.service.common.SendDmqMessage;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class OrderTimeoutMessageReceiver {

    private static final String TIMEOUT_REMARK = "timeout_no_notify_10m";

    private final CollectionOrderMapper collectionOrderMapper;
    private final PayOrderMapper payOrderMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final SendDmqMessage sendDmqMessage;

    public OrderTimeoutMessageReceiver(
            CollectionOrderMapper collectionOrderMapper,
            PayOrderMapper payOrderMapper,
            UserMapper userMapper,
            RedisUtil redisUtil,
            SendDmqMessage sendDmqMessage) {
        this.collectionOrderMapper = collectionOrderMapper;
        this.payOrderMapper = payOrderMapper;
        this.userMapper = userMapper;
        this.redisUtil = redisUtil;
        this.sendDmqMessage = sendDmqMessage;
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
                if (updated >0) {
                    notifyAdminsForTimeout(
                            timeoutMessage.getTransactionNo(),
                            timeoutMessage.getCreateTime(),
                            NotificationComponentType.Collection_Component,
                            NotificationComponentType.Collection_Title,
                            "collection");
                }
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
                if (updated >0) {
                    notifyAdminsForTimeout(
                            timeoutMessage.getTransactionNo(),
                            timeoutMessage.getCreateTime(),
                            NotificationComponentType.PayOut_Component,
                            NotificationComponentType.PayOut_Title,
                            "payOut");
                }
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

    private void notifyAdminsForTimeout(String transactionNo,
                                        Long timestamp,
                                        String path,
                                        String title,
                                        String orderTypeLabel) {
        log.info("start to notify admin to resolve timeout {} order", orderTypeLabel);
        List<String> adminUserIds = userMapper.listUserIdsByRoleId(CommonConstant.ROLE_ADMIN);
        if (adminUserIds == null || adminUserIds.isEmpty()) {
            log.warn("no admin user found, skip timeout {} order notification, transactionNo={}",
                    orderTypeLabel, transactionNo);
            return;
        }
        for (String adminUserId : adminUserIds) {
            com.pakgopay.data.entity.Message notifyMessage = new com.pakgopay.data.entity.Message();
            notifyMessage.setId(transactionNo);
            notifyMessage.setUserId(adminUserId);
            notifyMessage.setTimestamp(timestamp);
            notifyMessage.setRead(false);
            notifyMessage.setPath(path);
            notifyMessage.setTitle(title);
            notifyMessage.setContent(transactionNo);
            redisUtil.saveMessage(notifyMessage);
            sendDmqMessage.sendFanout("user-notify", notifyMessage);
        }
    }
}
