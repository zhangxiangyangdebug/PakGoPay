package com.pakgopay.service.transaction.handler;

import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaySystemHandler extends OrderHandler {

    @Override
    public Object handle(Object request) {
        // Handle in-house payout logic.
        log.info("PaySystemHandler handle, request={}", request);
        return "PK SYSTEM OK";
    }

    @Override
    public NotifyRequest handleNotify(String body) {
        // Handle in-house payout notify.
        return buildNotifyResponse(body);
    }
}
