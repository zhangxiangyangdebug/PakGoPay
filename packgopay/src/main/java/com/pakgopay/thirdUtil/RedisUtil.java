package com.pakgopay.thirdUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.data.entity.Message;
import com.pakgopay.service.common.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.pakgopay.common.constant.CommonConstant.*;

@Component
public class RedisUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisTemplate<String, Object> objectRedisTemplate;

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

    public void setObjectWithSecondExpire(String key, Object value, int expireTime) {
        objectRedisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
    }

    public boolean setIfAbsentWithSecondExpire(String key, String value, int expireTime) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public String getValue(String key) {
        if (redisTemplate.hasKey(key)) return redisTemplate.opsForValue().get(key);
        return null;
    }

    public <T> T getObjectValue(String key, Class<T> clazz) {
        if (!objectRedisTemplate.hasKey(key)) {
            return null;
        }
        Object value = objectRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        throw new IllegalStateException("redis value type mismatch, key=" + key + ", expected=" + clazz.getName()
                + ", actual=" + value.getClass().getName());
    }

    public boolean remove(String key) {
        return redisTemplate.delete(key);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long incrementBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Boolean expire(String key, int expireTimeSeconds) {
        return redisTemplate.expire(key, expireTimeSeconds, TimeUnit.SECONDS);
    }

    public Long addSetMember(String key, String member) {
        return redisTemplate.opsForSet().add(key, member);
    }

    public Set<String> getSetMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    public void saveMessage(Message msg) {
        String userKey = USER_ZSET_PREFIX + msg.getUserId();
        String bodyKey = buildBodyKey(msg.getUserId(), msg.getId());
        try {
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.opsForZSet().add(userKey, bodyKey, msg.getTimestamp());
            redisTemplate.opsForValue().set(bodyKey, json);
            redisTemplate.expire(bodyKey, java.time.Duration.ofSeconds(TTL_SECONDS));
        } catch (Exception e) {
            throw new RuntimeException("saveMessage failed", e);
        }
    }

    public List<Message> getMessages(String userId) {
        String userKey = USER_ZSET_PREFIX + userId;
        Set<String> ids = redisTemplate.opsForZSet().range(userKey, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = ids.stream().collect(Collectors.toList());
        List<String> jsons = redisTemplate.opsForValue().multiGet(keys);
        if (jsons == null) {
            return Collections.emptyList();
        }
        return jsons.stream()
                .filter(Objects::nonNull)
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

    }

    public void removeMessages(String userkey, String bodyKey) {
        Double score = redisTemplate.opsForZSet().score(userkey, bodyKey);

        //redisTemplate.opsForZSet().removeRangeByScore(userkey, score, score);
        redisTemplate.opsForZSet().remove(userkey, bodyKey);
        redisTemplate.delete(bodyKey);
    }

    public void removeAllMessages(String userId) {
        String userKey = USER_ZSET_PREFIX + userId;
        Set<String> bodyKeys = redisTemplate.opsForZSet().range(userKey, 0, -1);
        if (bodyKeys != null && !bodyKeys.isEmpty()) {
            redisTemplate.delete(bodyKeys);
        }
        redisTemplate.delete(userKey);
    }

    public Long noReadMessageCount(String userKey) {
        Long count = redisTemplate.opsForZSet().size(userKey);
        return count == null ? 0 : count;

    }

    private Message toMessage(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String buildBodyKey(String userId, String messageId) {
        return BODY_KEY_PREFIX + userId + ":" + messageId;
    }

}
