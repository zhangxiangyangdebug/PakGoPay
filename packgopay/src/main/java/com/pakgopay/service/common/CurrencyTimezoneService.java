package com.pakgopay.service.common;

import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CurrencyTimezoneService {

    @Autowired
    private CurrencyTypeMapper currencyTypeMapper;

    private final Map<String, ZoneId> zoneCache = new ConcurrentHashMap<>();

    /**
     * Resolve zone by currency code:
     * 1) currency.timezone from DB
     * 2) existing fallback mapping in CommonUtil
     */
    public ZoneId resolveZoneIdByCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return ZoneId.systemDefault();
        }
        String currencyKey = currency.trim().toUpperCase();
        return zoneCache.computeIfAbsent(currencyKey, this::loadZoneIdByCurrency);
    }

    private ZoneId loadZoneIdByCurrency(String currency) {
        try {
            String timezone = currencyTypeMapper.getTimezoneByCurrencyType(currency);
            if (timezone != null && !timezone.isBlank()) {
                try {
                    return ZoneId.of(timezone.trim());
                } catch (Exception e) {
                    log.warn("invalid timezone configured, currency={}, timezone={}, message={}",
                            currency, timezone, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("load timezone by currency failed, currency={}, message={}", currency, e.getMessage());
        }
        return CommonUtil.resolveZoneIdByCurrency(currency);
    }
}
