package com.pakgopay.service.order.handler;

import com.pakgopay.common.context.RouteContext;
import com.pakgopay.service.order.OrderHandler;
import org.springframework.stereotype.Component;

@Component
public class ColSystemHandler implements OrderHandler {

    @Override
    public Object handle(RouteContext ctx, Object request) {
        // 处理 PK 系统内逻辑
        return "PK SYSTEM OK";
    }
}
