package com.pakgopay.service.common;

import com.pakgopay.common.enums.OrderFlowStepEnum;
import com.pakgopay.data.response.OrderFlowLogQueryResponse;
import java.util.List;

public interface OrderFlowLogService {

    OrderFlowLogSession newCollectionSession(String transactionNo);

    OrderFlowLogSession newPayoutSession(String transactionNo);

    void logCollection(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload);

    void logPayout(String transactionNo, OrderFlowStepEnum step, Boolean success, Object payload);

    void logBatch(String transactionNo, boolean collection, List<OrderFlowLogEvent> events);

    OrderFlowLogQueryResponse listByTransactionNo(String transactionNo);
}
