package com.pakgopay.timer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class ReportTimer {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MerchantReportTask merchantReportTask;

    @Autowired
    private AgentReportTask agentReportTask;

    @Autowired
    private ChannelReportTask channelReportTask;

    @Autowired
    private CurrencyReportTask currencyReportTask;

    @Autowired
    private PaymentReportTask paymentReportTask;

    // every hour on the hour
    @Scheduled(cron = "0 0 * * * ?")
    public void run() {
        log.info("start exec schedule report task");
        String lockKey = "job:hourly_report:lock";
        String lockValue = UUID.randomUUID().toString();

        // lock time 70 min（should > task execute time）
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMinutes(70));

        if (!Boolean.TRUE.equals(success)) {
            log.warn("report task is running");
            return;
        }

        try {
            doHourlyReport();
        } finally {
            // 防止误删别人锁
            String val = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(val)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private void doHourlyReport() {

        try {
            merchantReportTask.doHourlyReport();

            agentReportTask.doHourlyReport();

            channelReportTask.doHourlyReport();

            currencyReportTask.doHourlyReport();

            paymentReportTask.doHourlyReport();
        } catch (Exception e) {
            log.error("doHourlyReport failed, error message: {}", e.getMessage());
        }
    }
}
