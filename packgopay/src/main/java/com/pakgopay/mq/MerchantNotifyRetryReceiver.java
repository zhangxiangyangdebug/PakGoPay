package com.pakgopay.mq;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.config.RabbitConfig;
import com.pakgopay.data.entity.transaction.MerchantNotifyRetryMessage;
import com.pakgopay.service.common.MerchantNotifyRetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MerchantNotifyRetryReceiver {

    private final MerchantNotifyRetryService merchantNotifyRetryService;

    public MerchantNotifyRetryReceiver(MerchantNotifyRetryService merchantNotifyRetryService) {
        this.merchantNotifyRetryService = merchantNotifyRetryService;
    }

    @RabbitListener(
            containerFactory = "orderTimeoutRabbitListenerContainerFactory",
            concurrency = "10",
            queues = {
                    RabbitConfig.MERCHANT_NOTIFY_COLLECTION_QUEUE,
                    RabbitConfig.MERCHANT_NOTIFY_PAYING_QUEUE
            })
    public void onMessage(Message message) {
        // Route retry message by consumed queue.
        String queue = message.getMessageProperties().getConsumerQueue();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("merchant notify retry mq received, queue={}, bodyLength={}", queue, body.length());
        try {
            MerchantNotifyRetryMessage retryMessage = JSON.parseObject(body, MerchantNotifyRetryMessage.class);
            boolean collection = RabbitConfig.MERCHANT_NOTIFY_COLLECTION_QUEUE.equals(queue);
            log.info("merchant notify retry mq parsed, queue={}, type={}, transactionNo={}, attempt={}, maxAttempts={}",
                    queue,
                    collection ? "collection" : "payout",
                    retryMessage == null ? null : retryMessage.getTransactionNo(),
                    retryMessage == null ? null : retryMessage.getAttempt(),
                    retryMessage == null ? null : retryMessage.getMaxAttempts());
            merchantNotifyRetryService.handleRetryMessage(collection, retryMessage);
        } catch (Exception e) {
            log.error("merchant notify retry mq handle failed, queue={}, body={}, message={}",
                    queue, body, e.getMessage());
        }
    }
}
