package com.pakgopay.service.order;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MerchantCheckService {

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    public boolean existsColMerchantOrderNo(String merchantOrderNo) {
        // TODO xiaoyou 从redis中获取判断，不存在则存入，数据写入数据库后，删除该数据
        Integer count = collectionOrderMapper.isExitMerchantOrderNo(merchantOrderNo);
        return CommonConstant.ZERO.equals(count);
    }

    public boolean existsPayMerchantOrderNo(String merchantOrderNo) {
        // TODO xiaoyou 从redis中获取判断，不存在则存入，数据写入数据库后，删除该数据
        Integer count = collectionOrderMapper.isExitMerchantOrderNo(merchantOrderNo);
        return CommonConstant.ZERO.equals(count);
    }

    /**
     * check user is enabled
     *
     * @return result
     */
    public boolean isEnableMerchant(Integer merchantStatus, Long agentUserId) {
        // xiaoyou 商户启用状态
        if (!CommonConstant.ENABLE_STATUS_ENABLE.equals(merchantStatus)) {
            return false;
        }

        // xiaoyou 商户无代理，无需判断代理是否启用
        if (agentUserId == null) {
            return true;
        }

        // xiaoyou 查询所有代理信息
        AgentInfoDto agentInfoDto = agentInfoMapper.findByUserId(agentUserId);
        boolean agentEnable = CommonConstant.ENABLE_STATUS_ENABLE.equals(agentInfoDto.getStatus());

        log.info("agent enable status {}", agentEnable);
        return agentEnable;
    }

    /**
     * check user ip is in white list (Collection Order)
     *
     * @param userId   user Id
     * @param clientIp Client Ip
     * @return check result
     */
    @Cacheable(cacheNames = "col_ip_isAllow", key = "#userId")
    public boolean isColIpAllowed(Long userId, String clientIp, String whiteIps) {
        Set<String> allowedIps = parseIpWhitelist(whiteIps);
        return allowedIps.contains(clientIp);
    }


    /**
     * update user ip white list and clear col_ip_isAllow cache (Collection Order)
     *
     * @param userId user Id
     * @param ips    ip list
     */
    @CacheEvict(cacheNames = "col_ip_isAllow", key = "#userId")
    @Transactional
    public void updateColIpWhitelist(Long userId, String ips) {
        MerchantInfoDto merchantInfoDto = merchantInfoMapper.findByUserId(userId);
        merchantInfoDto.setColWhiteIps(ips);
        merchantInfoMapper.upDateColWhiteIpsByUserId(userId, ips);
    }

    /**
     * check user ip is in white list (Pay Order)
     *
     * @param userId   user Id
     * @param clientIp Client Ip
     * @return check result
     */
    @Cacheable(cacheNames = "pay_ip_isAllow", key = "#userId")
    public boolean isPayIpAllowed(Long userId, String clientIp, String whiteIps) {
        Set<String> allowedIps = parseIpWhitelist(whiteIps);
        return allowedIps.contains(clientIp);
    }

    /**
     * update user ip white list and clear col_ip_isAllow cache (Pay Order)
     *
     * @param userId user Id
     * @param ips    ip list
     */
    @CacheEvict(cacheNames = "pay_ip_isAllow", key = "#userId")
    @Transactional
    public void updatePayIpWhitelist(Long userId, String ips) {
        MerchantInfoDto merchantInfoDto = merchantInfoMapper.findByUserId(userId);
        merchantInfoDto.setPayWhiteIps(ips);
        merchantInfoMapper.upDatePayWhiteIpsByUserId(userId, ips);
    }

    public MerchantInfoDto getConfigurationInfo(Long userId) {
        return merchantInfoMapper.findByUserId(userId);
    }

    private Set<String> parseIpWhitelist(String ipWhitelist) {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return Set.of("127.0.0.1");
        }

        return Arrays.stream(ipWhitelist.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
