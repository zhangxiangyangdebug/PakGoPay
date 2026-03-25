package com.pakgopay.util;

import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TimingTraceContext {

    private static final ThreadLocal<LinkedHashMap<String, Long>> TRACE =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private TimingTraceContext() {
    }

    public static void clear() {
        TRACE.remove();
    }

    public static void record(String stepName, long durationMs) {
        if (stepName == null || stepName.isBlank()) {
            return;
        }
        TRACE.get().put(stepName, durationMs);
    }

    public static String toJson() {
        Map<String, Long> snapshot = TRACE.get();
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(snapshot);
    }
}
