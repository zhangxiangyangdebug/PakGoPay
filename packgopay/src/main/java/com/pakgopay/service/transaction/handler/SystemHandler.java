package com.pakgopay.service.transaction.handler;

import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SystemHandler extends OrderHandler {

    @Override
    public Object handleCol(Object request) {
        // Handle in-house collection logic.
        log.info("ColSystemHandler handle, request={}", request);
        return "PK SYSTEM OK";
    }

    @Override
    public Object handlePay(Object request) {
        // Handle in-house payout logic.
        log.info("PaySystemHandler handle, request={}", request);
        return "PK SYSTEM OK";
    }

    @Override
    public NotifyRequest handleNotify(Map<String, Object> body) {
        // Handle in-house collection notify.
        return buildNotifyResponse(body);
    }
}
