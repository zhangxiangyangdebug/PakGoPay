package com.pakgopay.service.transaction;

import com.pakgopay.common.enums.OrderScope;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.service.transaction.handler.ThirdPartyAlipayHandler;
import com.pakgopay.service.transaction.handler.ThirdPartyBankTransferHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderHandlerFactory implements ApplicationContextAware {
    private static final String DEFAULT_PAYMENT_NO = "*";
    private static final Map<String, Map<String, Map<String, Class<? extends OrderHandler>>>> handlerMap = new ConcurrentHashMap<>();
    private static ApplicationContext applicationContext;

    static {
        registerDefaultForCurrencies(
                new String[]{"PKR", "US", "VND", "CNY"},
                ThirdPartyBankTransferHandler.class);
        registerExplicitMappings();
    }

    /**
     * 获取处理器
     */
    public static OrderHandler get(OrderScope scope, String currency, String paymentNo) {
        String currencyKey = normalizeCurrency(currency);
        String scopeKey = scope == null ? null : scope.getMessage();
        String paymentKey = normalizePaymentNo(paymentNo);

        try {
            ensureAppContext();
            Map<String, Class<? extends OrderHandler>> paymentHandlers =
                    resolvePaymentHandlers(currencyKey, scopeKey);
            Class<? extends OrderHandler> handlerClass =
                    resolveHandlerClass(paymentHandlers, paymentKey);
            return applicationContext.getBean(handlerClass);
        } catch (IllegalStateException e) {
            throw new PakGoPayException(ResultCode.PAYMENT_NOT_SUPPORT_CURRENCY, e.getMessage());
        }
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "";
        }
        return currency.trim().toUpperCase();
    }

    public static void register(String currency, OrderScope scope, String paymentNo,
                                 Class<? extends OrderHandler> handlerClass) {
        String currencyKey = normalizeCurrency(currency);
        handlerMap.computeIfAbsent(currencyKey, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(scope.getMessage(), key -> new ConcurrentHashMap<>())
                .put(normalizePaymentNo(paymentNo), handlerClass);
    }

    private static void registerDefaultForCurrencies(
            String[] currencies,
            Class<? extends OrderHandler> defaultHandler) {
        for (String currency : currencies) {
            register(currency, OrderScope.SYSTEM, DEFAULT_PAYMENT_NO, defaultHandler);
            register(currency, OrderScope.THIRD_PARTY, DEFAULT_PAYMENT_NO, defaultHandler);
        }
    }

    private static void registerExplicitMappings() {
        // CNY + THIRD_PARTY + ALIPAY -> ColThirdPartyAlipayHandler
        register("CNY", OrderScope.THIRD_PARTY, "ALIPAY", ThirdPartyAlipayHandler.class);
    }

    private static String normalizePaymentNo(String paymentNo) {
        if (paymentNo == null || paymentNo.isBlank()) {
            return DEFAULT_PAYMENT_NO;
        }
        return paymentNo.trim().toUpperCase();
    }

    private static void ensureAppContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is not initialized");
        }
    }

    private static Map<String, Class<? extends OrderHandler>> resolvePaymentHandlers(
            String currencyKey, String scopeKey) {
        Map<String, Map<String, Class<? extends OrderHandler>>> scopeHandlers = handlerMap.get(currencyKey);
        if (scopeHandlers == null) {
            throw new IllegalStateException("No OrderHandler found for currency: " + currencyKey);
        }
        if (scopeKey == null || scopeKey.isBlank()) {
            throw new IllegalStateException("OrderScope is required");
        }
        Map<String, Class<? extends OrderHandler>> paymentHandlers = scopeHandlers.get(scopeKey);
        if (paymentHandlers == null) {
            throw new IllegalStateException("No OrderHandler found for key: " + scopeKey);
        }
        return paymentHandlers;
    }

    private static Class<? extends OrderHandler> resolveHandlerClass(
            Map<String, Class<? extends OrderHandler>> paymentHandlers,
            String paymentKey) {
        Class<? extends OrderHandler> handlerClass = paymentHandlers.get(paymentKey);
        if (handlerClass == null) {
            handlerClass = paymentHandlers.get(DEFAULT_PAYMENT_NO);
        }
        if (handlerClass == null) {
            throw new IllegalStateException("No OrderHandler found for paymentNo: " + paymentKey);
        }
        return handlerClass;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }
}
