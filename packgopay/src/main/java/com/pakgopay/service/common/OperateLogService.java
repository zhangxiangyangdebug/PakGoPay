package com.pakgopay.service.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.mapper.OperateLogMapper;
import com.pakgopay.mapper.dto.OperateLogDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class OperateLogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "pwd", "secret", "key", "token", "sign",
            "googlecode", "code", "apikey", "signkey", "loginsecret", "apisecret"
    );

    @Autowired
    private OperateLogMapper operateLogMapper;

    public void write(OperateInterfaceEnum operateInterface, String operatorUserId, Object requestBody) {
        if (operateInterface == null) {
            log.warn("operate log skip, operateInterface is null");
            return;
        }
        if (!StringUtils.hasText(operatorUserId)) {
            log.warn("operate log skip, operatorUserId is empty, interface={}", operateInterface.getMessage());
            return;
        }
        long now = Instant.now().getEpochSecond();
        OperateLogDto dto = new OperateLogDto();
        dto.setOperateType(operateInterface.getOperateType());
        dto.setOperateName(operateInterface.getMessage());
        dto.setOperateParams(buildSafeOperateParams(requestBody));
        dto.setOperateUserId(operatorUserId);
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        try {
            int affected = operateLogMapper.insert(dto);
            log.info("operate log inserted, affected={}, dto={}", affected, dto);
        } catch (Exception e) {
            log.error("operate log insert failed, interface={}, operatorUserId={}, message={}",
                    operateInterface.getMessage(), operatorUserId, e.getMessage());
        }
    }

    private String buildSafeOperateParams(Object requestBody) {
        if (requestBody == null) {
            return null;
        }
        try {
            // Use Jackson first so Java records are serialized as normal JSON objects.
            String requestJson = OBJECT_MAPPER.writeValueAsString(requestBody);
            Object jsonObject = JSON.parse(requestJson);
            Object sanitized = sanitizeValue(jsonObject, null);
            return JSON.toJSONString(sanitized);
        } catch (Exception e) {
            log.warn("operate log params sanitize failed, message={}", e.getMessage());
            return "{\"_masked\":\"sanitize_failed\"}";
        }
    }

    private Object sanitizeValue(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject jsonObject) {
            JSONObject result = new JSONObject(true);
            for (String currentKey : jsonObject.keySet()) {
                Object currentValue = jsonObject.get(currentKey);
                if (isSensitiveKey(currentKey)) {
                    result.put(currentKey, maskValue(currentValue));
                } else {
                    result.put(currentKey, sanitizeValue(currentValue, currentKey));
                }
            }
            return result;
        }
        if (value instanceof JSONArray jsonArray) {
            JSONArray result = new JSONArray();
            for (Object item : jsonArray) {
                result.add(sanitizeValue(item, key));
            }
            return result;
        }
        if (isSensitiveKey(key)) {
            return maskValue(value);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerKey.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String maskValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }
}
