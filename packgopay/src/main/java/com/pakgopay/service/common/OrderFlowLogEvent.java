package com.pakgopay.service.common;

import com.pakgopay.common.enums.OrderFlowStepEnum;

/**
 * One flow log event item for batch logging.
 */
public class OrderFlowLogEvent {

    private final OrderFlowStepEnum step;
    private final Boolean success;
    private final Object payload;

    public OrderFlowLogEvent(OrderFlowStepEnum step, Boolean success, Object payload) {
        this.step = step;
        this.success = success;
        this.payload = payload;
    }

    public OrderFlowStepEnum getStep() {
        return step;
    }

    public Boolean getSuccess() {
        return success;
    }

    public Object getPayload() {
        return payload;
    }
}

