package com.pakgopay.service.transaction.handler;

import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.data.entity.transaction.CollectionQueryEntity;
import com.pakgopay.data.entity.transaction.CollectionCreateEntity;
import com.pakgopay.data.entity.transaction.PayQueryEntity;
import com.pakgopay.data.entity.transaction.PayCreateEntity;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SystemHandler extends OrderHandler {

    @Override
    public Object handleCol(CollectionCreateEntity request) {
        // Handle in-house collection logic.
        log.info("ColSystemHandler handle, request={}", request);
        return "PK SYSTEM OK";
    }

    @Override
    public Object handlePay(PayCreateEntity request) {
        // Handle in-house payout logic.
        log.info("PaySystemHandler handle, request={}", request);
        return "PK SYSTEM OK";
    }

    @Override
    public TransactionStatus handleCollectionQuery(CollectionQueryEntity request) {
        throw new UnsupportedOperationException("collection query is not supported for SystemHandler");
    }

    @Override
    public TransactionStatus handlePayQuery(PayQueryEntity request) {
        throw new UnsupportedOperationException("pay query is not supported for SystemHandler");
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body) {
        // Handle in-house collection notify.
        return buildNotifyResponse(body);
    }

    @Override
    public Object getNotifySuccessResponse() {
        return "ok";
    }
}
