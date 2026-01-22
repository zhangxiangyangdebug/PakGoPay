package com.pakgopay.service.transaction;

public interface OrderHandler {
    /**
     * 处理业务：下单/查询/回调/对账等
     */
    Object handle(Object request);
}
