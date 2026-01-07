package com.pakgopay.service.report.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.report.MerchantReportResponse;
import com.pakgopay.entity.MerchantReportEntity;
import com.pakgopay.mapper.MerchantReportMapper;
import com.pakgopay.mapper.dto.MerchantReportDto;
import com.pakgopay.service.balance.BalanceService;
import com.pakgopay.service.common.CommonService;
import com.pakgopay.service.report.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private BalanceService balanceService;

    @Override
    public CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException {
        log.info("queryMerchantReport start");
        String userId = merchantReportRequest.getUserId();
        Integer roleId = commonService.getRoleIdByUserId(userId);
        if (!CommonConstant.MERCHANT_REPORT_SUPPORT_ROLE.contains(roleId)) {
            log.error("user not support view merchant report");
            return CommonResponse.fail(ResultCode.FAIL, "user not support view merchant report");
        }

        MerchantReportEntity entity = new MerchantReportEntity();
        entity.setOrderType(merchantReportRequest.getOrderType());
        entity.setStartTime(Long.valueOf(merchantReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(merchantReportRequest.getEndTime()));
        entity.setPageNo(merchantReportRequest.getPageNo());
        entity.setPageSize(merchantReportRequest.getPageSize());
        log.info("roleId: {}, query condition is {}", roleId, JSON.toJSONString(entity));

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

        log.info("queryChannelReport end");
        return null;
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
            Long totalNumber = merchantReportMapper.countByQuery(entity);
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
}
