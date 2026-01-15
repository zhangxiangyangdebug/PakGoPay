package com.pakgopay.service.report.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.report.*;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.report.*;
import com.pakgopay.data.entity.report.*;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.balance.BalanceService;
import com.pakgopay.service.common.CommonService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.service.report.ReportService;
import com.pakgopay.util.CommontUtil;
import com.pakgopay.util.ExportFileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private AgentReportMapper agentReportMapper;

    @Autowired
    private CurrencyReportMapper currencyReportMapper;

    @Autowired
    private PaymentReportMapper paymentReportMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private BalanceService balanceService;

    @Override
    public CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException {
        log.info("queryMerchantReport start");
        checkUserRolePermission(
                merchantReportRequest.getUserId(), CommonConstant.MERCHANT_REPORT_SUPPORT_ROLE);
        MerchantReportResponse response = getMerchantReportResponse(merchantReportRequest);

        log.info("queryMerchantReport end");
        return CommonResponse.success(response);
    }

    private MerchantReportResponse getMerchantReportResponse(
            MerchantReportRequest merchantReportRequest) throws PakGoPayException {

        MerchantReportEntity entity = new MerchantReportEntity();
        entity.setMerchantName(merchantReportRequest.getMerchantName());
        entity.setOrderType(merchantReportRequest.getOrderType());
        entity.setCurrency(merchantReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(merchantReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(merchantReportRequest.getEndTime()));
        entity.setPageNo(merchantReportRequest.getPageNo());
        entity.setPageSize(merchantReportRequest.getPageSize());
        log.info("queryMerchantReport condition is {}", JSON.toJSONString(entity));

        return queryMerchantReportData(entity, merchantReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryChannelReport(ChannelReportRequest channelReportRequest) throws PakGoPayException {
        log.info("queryChannelReport start");
        checkUserRolePermission(
                channelReportRequest.getUserId(), CommonConstant.CHANNEL_REPORT_SUPPORT_ROLE);
        ChannelReportResponse response = getChannelReportResponse(channelReportRequest);

        log.info("queryChannelReport end");
        return CommonResponse.success(response);
    }

    private ChannelReportResponse getChannelReportResponse(
            ChannelReportRequest channelReportRequest) throws PakGoPayException {
        ChannelReportEntity entity = new ChannelReportEntity();
        entity.setChannelId(Long.valueOf(channelReportRequest.getChannelId()));
        entity.setOrderType(channelReportRequest.getOrderType());
        entity.setCurrency(channelReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(channelReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(channelReportRequest.getEndTime()));
        entity.setPageNo(channelReportRequest.getPageNo());
        entity.setPageSize(channelReportRequest.getPageSize());
        log.info("queryChannelReport condition is {}", JSON.toJSONString(entity));

        return queryChannelReportData(entity, channelReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryAgentReport(AgentReportRequest agentReportRequest) throws PakGoPayException {
        log.info("queryAgentReport start");
        checkUserRolePermission(
                agentReportRequest.getUserId(), CommonConstant.AGENT_REPORT_SUPPORT_ROLE);
        AgentReportResponse response = getAgentReportResponse(agentReportRequest);

        log.info("queryAgentReport end");
        return CommonResponse.success(response);
    }

    private AgentReportResponse getAgentReportResponse(AgentReportRequest agentReportRequest) throws PakGoPayException {
        AgentReportEntity entity = new AgentReportEntity();
        entity.setUserId(agentReportRequest.getUserId());
        entity.setOrderType(agentReportRequest.getOrderType());
        entity.setCurrency(agentReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(agentReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(agentReportRequest.getEndTime()));
        entity.setPageNo(agentReportRequest.getPageNo());
        entity.setPageSize(agentReportRequest.getPageSize());
        log.info("queryAgentReport condition is {}", JSON.toJSONString(entity));

        return queryAgentReportData(entity, agentReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryCurrencyReport(BaseReportRequest currencyReportRequest) throws PakGoPayException {
        log.info("queryCurrencyReport start");
        checkUserRolePermission(
                currencyReportRequest.getUserId(), CommonConstant.CURRENCY_REPORT_SUPPORT_ROLE);
        CurrencyReportResponse response = getCurrencyReportResponse(currencyReportRequest);

        log.info("queryCurrencyReport end");
        return CommonResponse.success(response);
    }

    private CurrencyReportResponse getCurrencyReportResponse(
            BaseReportRequest currencyReportRequest) throws PakGoPayException {
        BaseReportEntity entity = new BaseReportEntity();
        entity.setOrderType(currencyReportRequest.getOrderType());
        entity.setCurrency(currencyReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(currencyReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(currencyReportRequest.getEndTime()));
        entity.setPageNo(currencyReportRequest.getPageNo());
        entity.setPageSize(currencyReportRequest.getPageSize());
        log.info("queryCurrencyReport condition is {}", JSON.toJSONString(entity));

        return queryCurrencyReportData(entity, currencyReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryPaymentReport(PaymentReportRequest paymentReportRequest) throws PakGoPayException {
        log.info("queryPaymentReport start");
        checkUserRolePermission(
                paymentReportRequest.getUserId(), CommonConstant.PAYMENT_REPORT_SUPPORT_ROLE);

        PaymentReportResponse response = getPaymentReportResponse(paymentReportRequest);
        log.info("queryPaymentReport end");
        return CommonResponse.success(response);
    }

    private PaymentReportResponse getPaymentReportResponse(
            PaymentReportRequest paymentReportRequest) throws PakGoPayException {
        PaymentReportEntity entity = new PaymentReportEntity();
        entity.setPaymentId(Long.valueOf(paymentReportRequest.getPaymentId()));
        entity.setOrderType(paymentReportRequest.getOrderType());
        entity.setCurrency(paymentReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(paymentReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(paymentReportRequest.getEndTime()));
        entity.setPageNo(paymentReportRequest.getPageNo());
        entity.setPageSize(paymentReportRequest.getPageSize());
        log.info("queryPaymentReport condition is {}", JSON.toJSONString(entity));

        return queryPaymentReportData(entity, paymentReportRequest.getIsNeedCardData());
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
            Map<String, Map<String, BigDecimal>> cardInfo = balanceService.getBalanceInfos(new ArrayList<String>(){{add(entity.getUserId());}});
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


    private AgentReportResponse queryAgentReportData(AgentReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("queryAgentReportData start");
        AgentReportResponse response = new AgentReportResponse();
        try {
            List<BigDecimal> balanceInfos = agentReportMapper.commissionInfosByQuery(entity);
            if (balanceInfos == null || balanceInfos.isEmpty()) {
                response.setTotalNumber(0);
            } else {
                response.setTotalNumber(balanceInfos.size());
            }

            List<AgentReportDto> agentReportDtoList = agentReportMapper.pageByQuery(entity);
            response.setAgentReportDtoList(agentReportDtoList);
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
            log.error("agentReportMapper queryAgentReportData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("queryAgentReportData end");
        return response;
    }

    private CurrencyReportResponse queryCurrencyReportData(BaseReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("queryCurrencyReportData start");
        CurrencyReportResponse response = new CurrencyReportResponse();
        try {
            List<BigDecimal> balanceInfos = currencyReportMapper.balanceInfosByQuery(entity);
            if (balanceInfos == null || balanceInfos.isEmpty()) {
                response.setTotalNumber(0);
            } else {
                response.setTotalNumber(balanceInfos.size());
            }

            List<CurrencyReportDto> agentReportDtoList = currencyReportMapper.pageByQuery(entity);
            response.setCurrencyReportDtoList(agentReportDtoList);
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
            log.error("currencyReportMapper queryCurrencyReportData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("queryCurrencyReportData end");
        return response;
    }

    private PaymentReportResponse queryPaymentReportData(PaymentReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("queryPaymentReportData start");
        PaymentReportResponse response = new PaymentReportResponse();
        try {
            List<BigDecimal> balanceInfos = paymentReportMapper.balanceInfosByQuery(entity);
            if (balanceInfos == null || balanceInfos.isEmpty()) {
                response.setTotalNumber(0);
            } else {
                response.setTotalNumber(balanceInfos.size());
            }

            List<PaymentReportDto> channelReportDtoList = paymentReportMapper.pageByQuery(entity);
            response.setPaymentReportDtoList(channelReportDtoList);
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
            log.error("paymentReportMapper queryPaymentReportData failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("queryPaymentReportData end");
        return response;
    }

    private void checkUserRolePermission(String userId, List<Integer> permissionList) throws PakGoPayException {
        log.info("checkUserRolePermission start");
        Integer roleId = commonService.getRoleIdByUserId(userId);
        if (!permissionList.contains(roleId)) {
            log.error("user not support view this report");
            throw new PakGoPayException(ResultCode.FAIL, "user not support view this report");
        }
        log.info("checkUserRolePermission end, roleId: {}", roleId);
    }

    @Override
    public void exportMerchantReport(
            MerchantReportRequest merchantReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportMerchantReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<MerchantReportDto> colRes =
                ExportFileUtils.parseColumns(merchantReportRequest, ExportReportDataColumns.MERCHANT_REPORT_ALLOWED);

        // 2) Init paging params
        merchantReportRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        merchantReportRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                merchantReportRequest,
                (req) -> getMerchantReportResponse(req).getMerchantReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.MERCHANT_REPORT_EXPORT_FILE_NAME);

        log.info("exportMerchantReport end");
    }

    @Override
    public void exportChannelReport(
            ChannelReportRequest channelReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportChannelReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<ChannelReportDto> colRes =
                ExportFileUtils.parseColumns(channelReportRequest, ExportReportDataColumns.CHANNEL_REPORT_ALLOWED);

        // 2) Init paging params
        channelReportRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        channelReportRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                channelReportRequest,
                (req) -> getChannelReportResponse(req).getChannelReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_REPORT_EXPORT_FILE_NAME);

        log.info("exportChannelReport end");

    }

    @Override
    public void exportAgentReport(
            AgentReportRequest agentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportAgentReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<AgentReportDto> colRes =
                ExportFileUtils.parseColumns(agentReportRequest, ExportReportDataColumns.AGENT_REPORT_ALLOWED);

        // 2) Init paging params
        agentReportRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        agentReportRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                agentReportRequest,
                (req) -> getAgentReportResponse(req).getAgentReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.AGENT_REPORT_EXPORT_FILE_NAME);

        log.info("exportAgentReport end");
    }

    @Override
    public void exportCurrencyReport(
            BaseReportRequest currencyReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportCurrencyReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<CurrencyReportDto> colRes =
                ExportFileUtils.parseColumns(currencyReportRequest, ExportReportDataColumns.CURRENCY_REPORT_ALLOWED);

        // 2) Init paging params
        currencyReportRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        currencyReportRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                currencyReportRequest,
                (req) -> getCurrencyReportResponse(req).getCurrencyReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CURRENCY_REPORT_EXPORT_FILE_NAME);

        log.info("exportCurrencyReport end");
    }

    @Override
    public void exportPaymentReport(
            PaymentReportRequest paymentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportPaymentReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ExportFileUtils.ColumnParseResult<PaymentReportDto> colRes =
                ExportFileUtils.parseColumns(paymentReportRequest, ExportReportDataColumns.PAYMENT_REPORT_ALLOWED);

        // 2) Init paging params
        paymentReportRequest.setPageSize(ExportReportDataColumns.EXPORT_PAGE_SIZE);
        paymentReportRequest.setPageNo(1);

        // 3) Export by paging and multi-sheet writing
        ExportFileUtils.exportByPagingAndSheets(
                response,
                colRes.getHead(),
                paymentReportRequest,
                (req) -> getPaymentReportResponse(req).getPaymentReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.PAYMENT_REPORT_EXPORT_FILE_NAME
        );

        log.info("exportPaymentReport end");
    }
}
