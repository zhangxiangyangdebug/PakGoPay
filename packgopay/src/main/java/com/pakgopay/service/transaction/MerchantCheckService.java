package com.pakgopay.service.transaction;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.AgentInfoDto;
import com.pakgopay.mapper.dto.MerchantInfoDto;
import com.pakgopay.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

@Slf4j
@Service
public class MerchantCheckService {

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    /**
     * Check whether collection order number already exists under the same merchant.
     */
    public boolean existsColMerchantOrderNo(String merchantUserId, String merchantOrderNo) {
        log.info("existsColMerchantOrderNo start");
        long[] range = resolveCurrentMonthTimeRange();
        Integer count = collectionOrderMapper.isExitMerchantOrderNo(
                merchantUserId, merchantOrderNo, range[0], range[1]);
        return !CommonConstant.ZERO.equals(count);
    }

    /**
     * Check whether payout order number is still available under the same merchant.
     */
    public boolean existsPayMerchantOrderNo(String merchantUserId, String merchantOrderNo) {
        long[] range = resolveCurrentMonthTimeRange();
        Integer count = payOrderMapper.isExitMerchantOrderNo(
                merchantUserId, merchantOrderNo, range[0], range[1]);
        return CommonConstant.ZERO.equals(count);
    }

    private long[] resolveCurrentMonthTimeRange() {
        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        long start = monthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long end = nextMonthStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        return new long[]{start, end};
    }

    /**
     * check user is enabled
     *
     * @return result
     */
    public boolean isEnableMerchant(MerchantInfoDto merchantInfoDto) {
        log.info("isEnableMerchant start");
        if (merchantInfoDto == null) {
            log.warn("merchant info is null");
            return false;
        }
        // merchant enable status
        if (!CommonConstant.ENABLE_STATUS_ENABLE.equals(merchantInfoDto.getStatus())) {
            log.warn("merchantStatus is disable");
            return false;
        }

        // The merchant has no agent; additional checks are needed to determine if an agent is enabled.
        if (merchantInfoDto.getParentId() == null) {
            log.warn("merchant has not agent");
            return true;
        }

        AgentInfoDto agentInfoDto = merchantInfoDto.getCurrentAgentInfo();
        if (agentInfoDto == null) {
            log.warn("agent info is not exists");
            return false;
        }
        boolean agentEnable = CommonConstant.ENABLE_STATUS_ENABLE.equals(agentInfoDto.getStatus());

        log.info("agent enable status {}", agentEnable);
        return agentEnable;
    }

    /**
     * check user ip is in white list (Collection Order)
     *
     * @param clientIp Client Ip
     * @return check result
     */
    public boolean isColIpAllowed(String clientIp, String whiteIps) {
        log.info("validateCollectionRequest start");
        Set<String> allowedIps = CommonUtil.parseIpWhitelist(whiteIps);
        return allowedIps.contains(clientIp);
    }

    /**
     * check user ip is in white list (Pay Order)
     *
     * @param clientIp Client Ip
     * @return check result
     */
    public boolean isPayIpAllowed(String clientIp, String whiteIps) {
        Set<String> allowedIps = CommonUtil.parseIpWhitelist(whiteIps);
        return allowedIps.contains(clientIp);
    }
}
