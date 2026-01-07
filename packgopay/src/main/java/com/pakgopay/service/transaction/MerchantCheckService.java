package com.pakgopay.service.transaction;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.AgentInfoMapper;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.PayOrderMapper;
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

    @Autowired
    private PayOrderMapper payOrderMapper;

    public boolean existsColMerchantOrderNo(String merchantOrderNo) {
        log.info("existsColMerchantOrderNo start");
        // TODO xiaoyou 从redis中获取判断，不存在则存入，数据写入数据库后，删除该数据
        Integer count = collectionOrderMapper.isExitMerchantOrderNo(merchantOrderNo);
        return CommonConstant.ZERO.equals(count);
    }

    public boolean existsPayMerchantOrderNo(String merchantOrderNo) {
        // TODO xiaoyou 从redis中获取判断，不存在则存入，数据写入数据库后，删除该数据
        Integer count = payOrderMapper.isExitMerchantOrderNo(merchantOrderNo);
        return CommonConstant.ZERO.equals(count);
    }

    /**
     * check user is enabled
     *
     * @return result
     */
    public boolean isEnableMerchant(Integer merchantStatus, String agentUserId) {
        log.info("isEnableMerchant start");
        // xiaoyou 商户启用状态
        if (!CommonConstant.ENABLE_STATUS_ENABLE.equals(merchantStatus)) {
            log.warn("merchantStatus is disable");
            return false;
        }

        // xiaoyou 商户无代理，无需判断代理是否启用
        if (agentUserId == null) {
            log.warn("merchant has not agent");
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
    public boolean isColIpAllowed(String userId, String clientIp, String whiteIps) {
        log.info("validateCollectionRequest start");
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
    public void updateColIpWhitelist(String userId, String ips) {
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
    public boolean isPayIpAllowed(String userId, String clientIp, String whiteIps) {
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
    public void updatePayIpWhitelist(String userId, String ips) {
        MerchantInfoDto merchantInfoDto = merchantInfoMapper.findByUserId(userId);
        merchantInfoDto.setPayWhiteIps(ips);
        merchantInfoMapper.upDatePayWhiteIpsByUserId(userId, ips);
    }

    public MerchantInfoDto getMerchantInfo(String userId) throws PakGoPayException {
        log.info("getMerchantInfo start");
        try {
            return merchantInfoMapper.findByUserId(userId);
        } catch (Exception e) {
            log.error("merchantInfoMapper findByUserId failed, message: {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }
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
