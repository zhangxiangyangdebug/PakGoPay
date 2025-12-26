package com.pakgopay.service.order.handler;

import com.pakgopay.common.context.RouteContext;
import com.pakgopay.service.order.OrderHandler;
import org.springframework.stereotype.Component;

@Component
public class PayThirdPartyBankTransferHandler implements OrderHandler {

    @Override
    public Object handle(RouteContext ctx, Object request) {
        // 处理 PK 三方-银行转账通道类
        return "PK THIRD_PARTY BANK_TRANSFER OK";
    }
}