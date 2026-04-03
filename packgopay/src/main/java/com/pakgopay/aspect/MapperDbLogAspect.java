package com.pakgopay.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "pakgopay.db-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MapperDbLogAspect {

    @Value("${pakgopay.db-log.slow-ms:200}")
    private long slowMs;

    @Value("${pakgopay.db-log.sample-rate:0.01}")
    private double sampleRate;

    /**
     * Intercept all mapper calls and log cost.
     * On exception, print full stack for root cause diagnosis.
     */
    @Around("execution(* com.pakgopay.mapper..*.*(..))")
    public Object aroundMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        long proceedStart = start;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String mapperMethod = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String traceId = MDC.get("traceId");
        try {
            if (log.isDebugEnabled()) {
                log.debug("db call before proceed, traceId={}, mapper={}", traceId, mapperMethod);
            }
            Object result = joinPoint.proceed();
            long afterProceed = System.currentTimeMillis();
            long proceedCostMs = afterProceed - proceedStart;
            long resultHandlingStart = afterProceed;
            long costMs = System.currentTimeMillis() - start;
            long resultHandlingCostMs = System.currentTimeMillis() - resultHandlingStart;
            // Keep full logs for slow SQL, and sample normal SQL to reduce log I/O pressure.
            if (costMs >= slowMs || shouldSample()) {
                log.info("db call cost, traceId={}, mapper={}, costMs={}, proceedCostMs={}, resultHandlingCostMs={}",
                        traceId, mapperMethod, costMs, proceedCostMs, resultHandlingCostMs);
            } else if (log.isDebugEnabled()) {
                log.debug("db call after proceed, traceId={}, mapper={}, proceedCostMs={}, resultHandlingCostMs={}",
                        traceId, mapperMethod, proceedCostMs, resultHandlingCostMs);
            }
            return result;
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("db call failed, traceId={}, mapper={}, costMs={}, message={}",
                    traceId, mapperMethod, costMs, e.getMessage(), e);
            throw e;
        }
    }

    private boolean shouldSample() {
        if (sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
}
