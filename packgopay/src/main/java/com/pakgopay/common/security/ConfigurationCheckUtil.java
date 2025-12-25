package com.pakgopay.common.security;

import com.pakgopay.mapper.ConfigurationMapper;
import com.pakgopay.mapper.dto.ConfigurationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConfigurationCheckUtil {

    @Autowired
    ConfigurationMapper configurationMapper;

    private ConfigurationDto configurationDto;

    public void initData(String userId) {
        configurationDto = configurationMapper.findByUserId(userId).orElse(new ConfigurationDto());
    }

    /**
     * check user is enabled
     *
     * @return result
     */
    @Cacheable(cacheNames = "enable_status", key = "#userId")
    public boolean isEnableUser(String userId) {
        return configurationDto.getEnableStatus();
    }

    /**
     * check user ip is in white list (Collection Order)
     *
     * @param userId   user Id
     * @param clientIp Client Ip
     * @return check result
     */
    @Cacheable(cacheNames = "col_ip_isAllow", key = "#userId")
    public boolean isColIpAllowed(String userId, String clientIp) {
        Set<String> allowedIps = getColAllowedIps();
        return allowedIps.contains(clientIp);
    }

    /**
     * get user ip white list (Collection order)
     *
     * @return ip list
     */
    public Set<String> getColAllowedIps() {
        return parseIpWhitelist(configurationDto.getColWhiteIpList());
    }


    /**
     * update user ip white list and clear col_ip_isAllow cache (Collection Order)
     *
     * @param userId user Id
     * @param ips    ip list
     */
    @CacheEvict(cacheNames = "col_ip_isAllow", key = "#userId")
    @Transactional
    public void updateColIpWhitelist(String userId, String ips) {
        ConfigurationDto configurationDto =
                configurationMapper
                        .findByUserId(userId)
                        .orElseThrow(() -> new RuntimeException("商户不存在"));
        configurationDto.setColWhiteIpList(ips);
        configurationMapper.save(configurationDto);
    }

    /**
     * check user ip is in white list (Pay Order)
     *
     * @param userId   user Id
     * @param clientIp Client Ip
     * @return check result
     */
    @Cacheable(cacheNames = "pay_ip_isAllow", key = "#userId")
    public boolean isPayIpAllowed(String userId, String clientIp) {
        Set<String> allowedIps = getPayAllowedIps();
        return allowedIps.contains(clientIp);
    }


    /**
     * get user ip white list (Pay order)
     *
     * @return ip list
     */
    public Set<String> getPayAllowedIps() {
        return parseIpWhitelist(configurationDto.getPayWhiteIpList());
    }

    /**
     * update user ip white list and clear col_ip_isAllow cache (Pay Order)
     *
     * @param userId user Id
     * @param ips    ip list
     */
    @CacheEvict(cacheNames = "pay_ip_isAllow", key = "#userId")
    @Transactional
    public void updatePayIpWhitelist(String userId, String ips) {
        ConfigurationDto configurationDto =
                configurationMapper
                        .findByUserId(userId)
                        .orElseThrow(() -> new RuntimeException("商户不存在"));
        configurationDto.setPayWhiteIpList(ips);
        configurationMapper.save(configurationDto);
    }

    private Set<String> parseIpWhitelist(String ipWhitelist) {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return Set.of("127.0.0.1");
        }

        return Arrays.stream(ipWhitelist.split(","))
                .map(String::trim)          // 去空格
                .filter(s -> !s.isEmpty())  // 去空值
                .collect(Collectors.toSet()); // 自动去重
    }
}
