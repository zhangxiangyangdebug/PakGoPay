package com.pakgopay.service.transaction;

import com.pakgopay.common.enums.OrderScope;
import com.pakgopay.common.enums.OrderType;
import com.pakgopay.service.transaction.handler.ColSystemHandler;
import com.pakgopay.service.transaction.handler.ColThirdPartyBankTransferHandler;
import com.pakgopay.service.transaction.handler.PaySystemHandler;
import com.pakgopay.service.transaction.handler.PayThirdPartyBankTransferHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderHandlerFactory implements ApplicationContextAware {
    private static final Map<String, Map<String, Class<? extends OrderHandler>>> handlerMap = new ConcurrentHashMap<>();
    private static ApplicationContext applicationContext;

    static {
        registerForCurrencies(
                new String[]{"PKR", "US", "VND"},
                ColSystemHandler.class,
                ColThirdPartyBankTransferHandler.class,
                PaySystemHandler.class,
                PayThirdPartyBankTransferHandler.class);
    }

    /**
     * 获取处理器
     */
    public static OrderHandler get(OrderType orderType, OrderScope scope, String currency) {
        String currencyKey = normalizeCurrency(currency);
        Map<String, Class<? extends OrderHandler>> currencyHandlers = handlerMap.get(currencyKey);
        if (currencyHandlers == null) {
            throw new IllegalStateException("No OrderHandler found for currency: " + currencyKey);
        }
        Class<? extends OrderHandler> handlerClass = currencyHandlers.get(buildKey(orderType, scope));
        if (handlerClass == null) {
            throw new IllegalStateException("No OrderHandler found for key: " + buildKey(orderType, scope));
        }
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is not initialized");
        }
        return applicationContext.getBean(handlerClass);
    }

    /**
     * Key 构造工具
     */
    public static String buildKey(OrderType orderType, OrderScope scope) {
        return orderType.getMessage() + ":" + scope.getMessage();
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "";
        }
        return currency.trim().toUpperCase();
    }

    private static void register(String currency, OrderType orderType, OrderScope scope,
                                 Class<? extends OrderHandler> handlerClass) {
        String currencyKey = normalizeCurrency(currency);
        handlerMap.computeIfAbsent(currencyKey, key -> new ConcurrentHashMap<>())
                .put(buildKey(orderType, scope), handlerClass);
    }

    private static void registerForCurrencies(
            String[] currencies,
            Class<? extends OrderHandler> collectionSystemHandler,
            Class<? extends OrderHandler> collectionThirdPartyHandler,
            Class<? extends OrderHandler> paySystemHandler,
            Class<? extends OrderHandler> payThirdPartyHandler) {
        for (String currency : currencies) {
            register(currency, OrderType.COLLECTION_ORDER, OrderScope.SYSTEM, collectionSystemHandler);
            register(currency, OrderType.COLLECTION_ORDER, OrderScope.THIRD_PARTY, collectionThirdPartyHandler);
            register(currency, OrderType.PAY_OUT_ORDER, OrderScope.SYSTEM, paySystemHandler);
            register(currency, OrderType.PAY_OUT_ORDER, OrderScope.THIRD_PARTY, payThirdPartyHandler);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }
}
