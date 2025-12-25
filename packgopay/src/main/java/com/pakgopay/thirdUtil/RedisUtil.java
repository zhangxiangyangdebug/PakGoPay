package com.pakgopay.thirdUtil;

import com.pakgopay.service.common.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value, AuthorizationService.refreshTokenExpirationTime*1000, TimeUnit.SECONDS);
    }

    /**
     *
     * @param key cache key
     * @param value cache value
     * @param expireTime  cache expireTime with Day
     */
    public void setWithDayExpire(String key, String value, int expireTime) {
        redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.DAYS);
    }

    public void setWithSecondExpire(String key, String value, int expireTime) {
        redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
    }

    public String getValue(String key) {
        if (redisTemplate.hasKey(key)) return redisTemplate.opsForValue().get(key);
        return null;
    }

    public boolean remove(String key) {
        return redisTemplate.delete(key);
    }
}
