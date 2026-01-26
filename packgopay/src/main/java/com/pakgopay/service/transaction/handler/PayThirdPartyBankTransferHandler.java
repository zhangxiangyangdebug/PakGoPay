package com.pakgopay.service.transaction.handler;

import com.pakgopay.data.reqeust.transaction.NotifyRequest;
import com.pakgopay.service.transaction.OrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayThirdPartyBankTransferHandler extends OrderHandler {

    @Override
    public Object handle(Object request) {
        // Handle third-party payout via bank transfer.
        log.info("PayThirdPartyBankTransferHandler handle, request={}", request);
        return "PK THIRD_PARTY BANK_TRANSFER OK";
    }

    @Override
    public NotifyRequest handleNotify(String body) {
        // Handle third-party payout notify (bank transfer).
        return buildNotifyResponse(body);
    }
}
