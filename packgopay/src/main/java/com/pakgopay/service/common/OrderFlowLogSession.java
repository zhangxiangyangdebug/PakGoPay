package com.pakgopay.service.common;

import com.pakgopay.common.enums.OrderFlowStepEnum;

public interface OrderFlowLogSession {

    void add(OrderFlowStepEnum step, Boolean success, Object payload);

    void flush();
}

