package com.pakgopay.mq;

import com.pakgopay.common.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

@Component
public class OrderMessageReceiver implements ChannelAwareMessageListener {
    private static final long[] RETRY_DELAYS_MS = {
        5_000L,
        30_000L,
        60_000L,
        180_000L,
        300_000L
    };

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = {RabbitConfig.TASK_COLLECTING_QUEUE, RabbitConfig.TASK_PAYING_QUEUE})
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        int retryCount = getRetryCount(message.getMessageProperties());
        try {
            process(message);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            if (isTimeoutException(ex) && retryCount < RETRY_DELAYS_MS.length) {
                int nextRetry = retryCount + 1;
                long baseDelay = RETRY_DELAYS_MS[retryCount];
                long jitter = ThreadLocalRandom.current().nextLong(0, 200);
                long delay = baseDelay + jitter;
                Message retryMessage = MessageBuilder.withBody(message.getBody())
                    .copyHeaders(message.getMessageProperties().getHeaders())
                    .setContentType(message.getMessageProperties().getContentType())
                    .setContentEncoding(message.getMessageProperties().getContentEncoding())
                    .setHeader("x-delay", delay)
                    .setHeader("x-retry-count", nextRetry)
                    .build();
                String targetQueue = message.getMessageProperties().getConsumerQueue();
                String routingKey = (targetQueue == null || targetQueue.isEmpty())
                    ? RabbitConfig.TASK_COLLECTING_QUEUE
                    : targetQueue;
                rabbitTemplate.send(RabbitConfig.DELAY_EXCHANGE, routingKey, retryMessage);
                channel.basicAck(tag, false);
            } else {
                // not retryable or exceed retry count, drop or move to dead-letter
                channel.basicAck(tag, false);
            }
        }
    }

    private int getRetryCount(MessageProperties props) {
        Object value = props.getHeaders().get("x-retry-count");
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof TimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void process(Message message) throws Exception {
        String queue = message.getMessageProperties().getConsumerQueue();
        if (RabbitConfig.TASK_PAYING_QUEUE.equals(queue)) {
            processPaying(message);
            return;
        }
        if (RabbitConfig.TASK_COLLECTING_QUEUE.equals(queue)) {
            processCollecting(message);
            return;
        }
        // fallback: use received routing key if available
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (RabbitConfig.TASK_PAYING_QUEUE.equals(routingKey)) {
            processPaying(message);
        } else {
            processCollecting(message);
        }
    }

    private void processCollecting(Message message) throws Exception {
        // TODO: implement collecting logic. Throw exception to trigger retry.
    }

    private void processPaying(Message message) throws Exception {
        // TODO: implement paying logic. Throw exception to trigger retry.
    }
}
