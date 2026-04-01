package com.pakgopay.util;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TimingTraceContext {

    private static final Logger log = LoggerFactory.getLogger(TimingTraceContext.class);

    private static final ThreadLocal<LinkedHashMap<String, Long>> TRACE =
            ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Boolean> ENABLED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TimingTraceContext() {
    }

    public static void begin() {
        ENABLED.set(Boolean.TRUE);
        TRACE.get().clear();
    }

    public static void begin(boolean enabled) {
        if (!enabled) {
            clear();
            return;
        }
        begin();
    }

    public static void clear() {
        TRACE.remove();
        ENABLED.remove();
    }

    public static boolean isEnabled() {
        return Boolean.TRUE.equals(ENABLED.get());
    }

    public static void record(String stepName, long durationMs) {
        if (!isEnabled()) {
            return;
        }
        if (stepName == null || stepName.isBlank()) {
            return;
        }
        TRACE.get().put(stepName, durationMs);
        log.info("timing trace, step={}, costMs={}", stepName, durationMs);
    }

    public static long methodStart() {
        if (!isEnabled()) {
            return -1L;
        }
        return System.currentTimeMillis();
    }

    public static void methodEnd(String stepName, long startMs) {
        if (!isEnabled() || startMs < 0) {
            return;
        }
        record(stepName, System.currentTimeMillis() - startMs);
    }

    public static String toJson() {
        Map<String, Long> snapshot = TRACE.get();
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(snapshot);
    }
}
