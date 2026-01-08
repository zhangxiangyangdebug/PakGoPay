package com.pakgopay.service.report.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.report.ChannelReportResponse;
import com.pakgopay.common.response.report.MerchantReportResponse;
import com.pakgopay.entity.report.ChannelReportEntity;
import com.pakgopay.entity.report.MerchantReportEntity;
import com.pakgopay.mapper.ChannelReportMapper;
import com.pakgopay.mapper.MerchantInfoMapper;
import com.pakgopay.mapper.MerchantReportMapper;
import com.pakgopay.mapper.dto.ChannelReportDto;
import com.pakgopay.mapper.dto.MerchantReportDto;
import com.pakgopay.service.balance.BalanceService;
import com.pakgopay.service.common.CommonService;
import com.pakgopay.service.report.ReportService;
import com.pakgopay.util.CommontUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private CommonService commonService;

    @Autowired
    private MerchantReportMapper merchantReportMapper;

    @Autowired
    private ChannelReportMapper channelReportMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private BalanceService balanceService;

    @Override
    public CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException {
        log.info("queryMerchantReport start");
        String userId = merchantReportRequest.getUserId();
        Integer roleId = checkUserRolePermission(
                merchantReportRequest.getUserId(), CommonConstant.MERCHANT_REPORT_SUPPORT_ROLE);

        MerchantReportEntity entity = new MerchantReportEntity();
        entity.setMerchantName(merchantReportRequest.getMerchantName());
        entity.setOrderType(merchantReportRequest.getOrderType());
        entity.setCurrency(merchantReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(merchantReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(merchantReportRequest.getEndTime()));
        entity.setPageNo(merchantReportRequest.getPageNo());
        entity.setPageSize(merchantReportRequest.getPageSize());
        log.info("query condition is {}", JSON.toJSONString(entity));

        MerchantReportResponse response;
        switch (roleId) {
            case CommonConstant.ROLE_MERCHANT:
                entity.setUserId(userId);
                response = queryMerchantReportData(entity, merchantReportRequest.getIsNeedCardData());
                break;
            case CommonConstant.ROLE_ADMIN:
            case CommonConstant.ROLE_FINANCE:
                response = queryMerchantReportData(entity, merchantReportRequest.getIsNeedCardData());
                break;
            default:
                log.error("user not support view merchant report");
                return CommonResponse.fail(ResultCode.FAIL, "user not support view merchant report");
        }

        log.info("queryMerchantReport end");
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse queryChannelReport(ChannelReportRequest channelReportRequest) throws PakGoPayException {
        log.info("queryChannelReport start");
        checkUserRolePermission(
                channelReportRequest.getUserId(), CommonConstant.CHANNEL_REPORT_SUPPORT_ROLE);

        ChannelReportEntity entity = new ChannelReportEntity();
        entity.setChannelId(channelReportRequest.getChannelId());
        entity.setOrderType(channelReportRequest.getOrderType());
        entity.setCurrency(channelReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(channelReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(channelReportRequest.getEndTime()));
        entity.setPageNo(channelReportRequest.getPageNo());
        entity.setPageSize(channelReportRequest.getPageSize());
        log.info("query condition is {}", JSON.toJSONString(entity));

        ChannelReportResponse response = queryChannelReportData(entity, channelReportRequest.getIsNeedCardData());

        log.info("queryChannelReport end");
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse queryAgentReport(AgentReportRequest agentReportRequest) throws PakGoPayException {
        log.info("queryAgentReport start");

        log.info("queryAgentReport end");
        return null;
    }

    @Override
    public CommonResponse queryCurrencyReport(CurrencyReportRequest currencyReportRequest) throws PakGoPayException {
        log.info("queryCurrencyReport start");

        log.info("queryCurrencyReport end");
        return null;
    }

    @Override
    public CommonResponse queryPaymentReport(PaymentReportRequest paymentReportRequest) throws PakGoPayException {
        log.info("queryPaymentReport start");

        log.info("queryPaymentReport end");
        return null;
    }

    private MerchantReportResponse queryMerchantReportData(MerchantReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("queryMerchantReportData start");
        MerchantReportResponse response = new MerchantReportResponse();
        try {
            // administrator searches for specified user by merchant name
            if (entity.getMerchantName() != null) {
                String merchantUserId = merchantInfoMapper.findByMerchantName(entity.getMerchantName());
                if (merchantUserId == null) {
                    log.error("findByMerchantName user is not exists, merchantName: {}", entity.getMerchantName());
                    throw new PakGoPayException(ResultCode.USER_IS_NOT_EXIST);
                }
                entity.setUserId(merchantUserId);
            }

            Integer totalNumber = merchantReportMapper.countByQuery(entity);
            List<MerchantReportDto> merchantReportDtoList = merchantReportMapper.pageByQuery(entity);

            response.setMerchantReportDtoList(merchantReportDtoList);
            response.setPageNo(entity.getPageNo());
            response.setPageSize(entity.getPageSize());
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("merchantReportMapper queryMerchantReportData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (isNeedCardData) {
            Map<String, Map<String, BigDecimal>> cardInfo = balanceService.getBalanceInfos(entity.getUserId());
            response.setCardInfo(cardInfo);
        }

        log.info("queryMerchantReportData end");
        return response;
    }

    private ChannelReportResponse queryChannelReportData(ChannelReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("queryChannelReportData start");
        ChannelReportResponse response = new ChannelReportResponse();
        try {
            List<BigDecimal> balanceInfos = channelReportMapper.balanceInfosByQuery(entity);
            if (balanceInfos == null || balanceInfos.isEmpty()) {
                response.setTotalNumber(0);
            } else {
                response.setTotalNumber(balanceInfos.size());
            }

            List<ChannelReportDto> channelReportDtoList = channelReportMapper.pageByQuery(entity);
            response.setChannelReportDtoList(channelReportDtoList);
            response.setPageNo(entity.getPageNo());
            response.setPageSize(entity.getPageSize());

            if (isNeedCardData) {
                Map<String, Map<String, BigDecimal>> cardInfo = new HashMap<>();
                Map<String, BigDecimal> currencyMap =
                        cardInfo.computeIfAbsent(entity.getCurrency(), k -> new HashMap<>());

                currencyMap.put("total", CommontUtil.sum(balanceInfos));
                response.setCardInfo(cardInfo);
            }
        } catch (Exception e) {
            log.error("channelReportMapper queryChannelReportData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("queryChannelReportData end");
        return response;
    }

    private Integer checkUserRolePermission(String userId, List<Integer> permissionList) throws PakGoPayException {
        log.info("checkUserRolePermission start");
        Integer roleId = commonService.getRoleIdByUserId(userId);
        if (!permissionList.contains(roleId)) {
            log.error("user not support view this report");
            throw new PakGoPayException(ResultCode.FAIL, "user not support view this report");
        }
        log.info("checkUserRolePermission end, roleId: {}", roleId);
        return roleId;
    }
}
