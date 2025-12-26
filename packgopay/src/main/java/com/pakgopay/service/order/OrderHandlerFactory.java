package com.pakgopay.service.order;

import com.pakgopay.common.enums.OrderScope;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.service.order.handler.ColThirdPartyBankTransferHandler;
import com.pakgopay.service.order.handler.PaySystemHandler;
import com.pakgopay.service.order.handler.PayThirdPartyBankTransferHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderHandlerFactory {
    private static final Map<String, OrderHandler> handlerMap = new ConcurrentHashMap<>() {{
        put(buildKey(OrderType.COLLECTION_ORDER,OrderScope.SYSTEM), new PaySystemHandler());
        put(buildKey(OrderType.COLLECTION_ORDER,OrderScope.THIRD_PARTY), new ColThirdPartyBankTransferHandler());
        put(buildKey(OrderType.PAY_OUT_ORDER,OrderScope.SYSTEM), new PaySystemHandler());
        put(buildKey(OrderType.PAY_OUT_ORDER,OrderScope.THIRD_PARTY), new PayThirdPartyBankTransferHandler());
    }};

    /**
     * 获取处理器
     */
    public static OrderHandler get(String key) {
        OrderHandler handler = handlerMap.get(key);
        if (handler == null) {
            throw new IllegalStateException("No OrderHandler found for key: " + key);
        }
        return handler;
    }

    /**
     * Key 构造工具
     */
    public static String buildKey(OrderType orderType, OrderScope scope) {
        return orderType.getMessage() + ":" + scope.getMessage();
    }
}
