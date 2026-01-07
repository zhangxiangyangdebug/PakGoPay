package com.pakgopay.service.transaction;

import com.pakgopay.common.context.RouteContext;

public interface OrderHandler {
    /**
     * 处理业务：下单/查询/回调/对账等
     */
    Object handle(RouteContext ctx, Object request);
}
