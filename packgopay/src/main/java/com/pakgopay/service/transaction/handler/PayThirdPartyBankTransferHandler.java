package com.pakgopay.service.transaction.handler;

import com.pakgopay.service.transaction.OrderHandler;
import org.springframework.stereotype.Component;

@Component
public class PayThirdPartyBankTransferHandler implements OrderHandler {

    @Override
    public Object handle(Object request) {
        // 处理 PK 三方-银行转账通道类
        return "PK THIRD_PARTY BANK_TRANSFER OK";
    }
}
