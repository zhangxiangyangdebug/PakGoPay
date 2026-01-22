package com.pakgopay.service.transaction.handler;

import com.pakgopay.service.transaction.OrderHandler;
import org.springframework.stereotype.Component;

@Component
public class ColSystemHandler implements OrderHandler {

    @Override
    public Object handle(Object request) {
        // 处理 PK 系统内逻辑
        return "PK SYSTEM OK";
    }
}
