package com.pakgopay.service.impl;

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
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.CommonService;
import com.pakgopay.service.common.ExportReportDataColumns;
import com.pakgopay.service.ReportService;
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
    public CommonResponse queryMerchantReports(MerchantReportRequest merchantReportRequest) throws PakGoPayException {
        log.info("queryMerchantReports start");
        checkUserRolePermission(
                merchantReportRequest.getUserId(), CommonConstant.MERCHANT_REPORT_SUPPORT_ROLE);
        MerchantReportResponse response = buildMerchantReportResponse(merchantReportRequest);

        log.info("queryMerchantReports end");
        return CommonResponse.success(response);
    }

    private MerchantReportResponse buildMerchantReportResponse(
            MerchantReportRequest merchantReportRequest) throws PakGoPayException {

        MerchantReportEntity entity = new MerchantReportEntity();
        entity.setMerchantName(merchantReportRequest.getMerchantName());
        entity.setOrderType(merchantReportRequest.getOrderType());
        entity.setCurrency(merchantReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(merchantReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(merchantReportRequest.getEndTime()));
        entity.setPageNo(merchantReportRequest.getPageNo());
        entity.setPageSize(merchantReportRequest.getPageSize());
        log.info("queryMerchantReports condition is {}", JSON.toJSONString(entity));

        return fetchMerchantReportPage(entity, merchantReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryChannelReports(ChannelReportRequest channelReportRequest) throws PakGoPayException {
        log.info("queryChannelReports start");
        checkUserRolePermission(
                channelReportRequest.getUserId(), CommonConstant.CHANNEL_REPORT_SUPPORT_ROLE);
        ChannelReportResponse response = buildChannelReportResponse(channelReportRequest);

        log.info("queryChannelReports end");
        return CommonResponse.success(response);
    }

    private ChannelReportResponse buildChannelReportResponse(
            ChannelReportRequest channelReportRequest) throws PakGoPayException {
        ChannelReportEntity entity = new ChannelReportEntity();
        entity.setChannelId(Long.valueOf(channelReportRequest.getChannelId()));
        entity.setOrderType(channelReportRequest.getOrderType());
        entity.setCurrency(channelReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(channelReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(channelReportRequest.getEndTime()));
        entity.setPageNo(channelReportRequest.getPageNo());
        entity.setPageSize(channelReportRequest.getPageSize());
        log.info("queryChannelReports condition is {}", JSON.toJSONString(entity));

        return fetchChannelReportPage(entity, channelReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryAgentReports(AgentReportRequest agentReportRequest) throws PakGoPayException {
        log.info("queryAgentReports start");
        checkUserRolePermission(
                agentReportRequest.getUserId(), CommonConstant.AGENT_REPORT_SUPPORT_ROLE);
        AgentReportResponse response = buildAgentReportResponse(agentReportRequest);

        log.info("queryAgentReports end");
        return CommonResponse.success(response);
    }

    private AgentReportResponse buildAgentReportResponse(AgentReportRequest agentReportRequest) throws PakGoPayException {
        AgentReportEntity entity = new AgentReportEntity();
        entity.setOrderType(agentReportRequest.getOrderType());
        entity.setCurrency(agentReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(agentReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(agentReportRequest.getEndTime()));
        entity.setPageNo(agentReportRequest.getPageNo());
        entity.setPageSize(agentReportRequest.getPageSize());
        log.info("queryAgentReports condition is {}", JSON.toJSONString(entity));

        return fetchAgentReportPage(entity, agentReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryCurrencyReports(BaseReportRequest currencyReportRequest) throws PakGoPayException {
        log.info("queryCurrencyReports start");
        checkUserRolePermission(
                currencyReportRequest.getUserId(), CommonConstant.CURRENCY_REPORT_SUPPORT_ROLE);
        CurrencyReportResponse response = buildCurrencyReportResponse(currencyReportRequest);

        log.info("queryCurrencyReports end");
        return CommonResponse.success(response);
    }

    private CurrencyReportResponse buildCurrencyReportResponse(
            BaseReportRequest currencyReportRequest) throws PakGoPayException {
        BaseReportEntity entity = new BaseReportEntity();
        entity.setOrderType(currencyReportRequest.getOrderType());
        entity.setCurrency(currencyReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(currencyReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(currencyReportRequest.getEndTime()));
        entity.setPageNo(currencyReportRequest.getPageNo());
        entity.setPageSize(currencyReportRequest.getPageSize());
        log.info("queryCurrencyReports condition is {}", JSON.toJSONString(entity));

        return fetchCurrencyReportPage(entity, currencyReportRequest.getIsNeedCardData());
    }

    @Override
    public CommonResponse queryPaymentReports(PaymentReportRequest paymentReportRequest) throws PakGoPayException {
        log.info("queryPaymentReports start");
        checkUserRolePermission(
                paymentReportRequest.getUserId(), CommonConstant.PAYMENT_REPORT_SUPPORT_ROLE);

        PaymentReportResponse response = buildPaymentReportResponse(paymentReportRequest);
        log.info("queryPaymentReports end");
        return CommonResponse.success(response);
    }

    private PaymentReportResponse buildPaymentReportResponse(
            PaymentReportRequest paymentReportRequest) throws PakGoPayException {
        PaymentReportEntity entity = new PaymentReportEntity();
        if (paymentReportRequest.getPaymentId() != null && !paymentReportRequest.getPaymentId().isEmpty()) {
            entity.setPaymentId(Long.valueOf(paymentReportRequest.getPaymentId()));
        }
        entity.setOrderType(paymentReportRequest.getOrderType());
        entity.setCurrency(paymentReportRequest.getCurrency());
        entity.setStartTime(Long.valueOf(paymentReportRequest.getStartTime()));
        entity.setEndTime(Long.valueOf(paymentReportRequest.getEndTime()));
        entity.setPageNo(paymentReportRequest.getPageNo());
        entity.setPageSize(paymentReportRequest.getPageSize());
        log.info("queryPaymentReports condition is {}", JSON.toJSONString(entity));

        return fetchPaymentReportPage(entity, paymentReportRequest.getIsNeedCardData());
    }

    private MerchantReportResponse fetchMerchantReportPage(MerchantReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("fetchMerchantReportPage start");
        MerchantReportResponse response = new MerchantReportResponse();
        try {
            // administrator searches for specified user by merchant name
            if (entity.getMerchantName() != null && !entity.getMerchantName().isEmpty()) {
                MerchantInfoDto merchantInfoDto = merchantInfoMapper.findByMerchantName(entity.getMerchantName())
                        .orElseThrow(() -> new PakGoPayException(
                                ResultCode.USER_IS_NOT_EXIST
                                , "merchant is not exists, merchantName:" + entity.getMerchantName()));
                entity.setUserId(merchantInfoDto.getUserId());
            }

            Integer totalNumber = merchantReportMapper.countByQuery(entity);
            List<MerchantReportDto> merchantReportDtoList = merchantReportMapper.pageByQuery(entity);

            response.setMerchantReportDtoList(merchantReportDtoList);
            response.setPageNo(entity.getPageNo());
            response.setPageSize(entity.getPageSize());
            response.setTotalNumber(totalNumber);
        } catch (PakGoPayException e) {
            log.error("fetchMerchantReportPage failed, code: {} message: {}", e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("merchantReportMapper fetchMerchantReportPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        if (isNeedCardData) {
            List<String> userIds = new ArrayList<>();
            if(entity.getUserId() != null){
                userIds.add(entity.getUserId());
            }
            Map<String, Map<String, BigDecimal>> cardInfo = balanceService.fetchBalanceSummaries(userIds);
            response.setCardInfo(cardInfo);
        }

        log.info("fetchMerchantReportPage end");
        return response;
    }

    private ChannelReportResponse fetchChannelReportPage(ChannelReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("fetchChannelReportPage start");
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
            log.error("channelReportMapper fetchChannelReportPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("fetchChannelReportPage end");
        return response;
    }


    private AgentReportResponse fetchAgentReportPage(AgentReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("fetchAgentReportPage start");
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
            log.error("agentReportMapper fetchAgentReportPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("fetchAgentReportPage end");
        return response;
    }

    private CurrencyReportResponse fetchCurrencyReportPage(BaseReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("fetchCurrencyReportPage start");
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
            log.error("currencyReportMapper fetchCurrencyReportPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("fetchCurrencyReportPage end");
        return response;
    }

    private PaymentReportResponse fetchPaymentReportPage(PaymentReportEntity entity, Boolean isNeedCardData) throws PakGoPayException {
        log.info("fetchPaymentReportPage start");
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
            log.error("paymentReportMapper fetchPaymentReportPage failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("fetchPaymentReportPage end");
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
    public void exportMerchantReports(
            MerchantReportRequest merchantReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportMerchantReports start");

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
                (req) -> buildMerchantReportResponse(req).getMerchantReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.MERCHANT_REPORT_EXPORT_FILE_NAME);

        log.info("exportMerchantReports end");
    }

    @Override
    public void exportChannelReports(
            ChannelReportRequest channelReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportChannelReports start");

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
                (req) -> buildChannelReportResponse(req).getChannelReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CHANNEL_REPORT_EXPORT_FILE_NAME);

        log.info("exportChannelReports end");

    }

    @Override
    public void exportAgentReports(
            AgentReportRequest agentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportAgentReports start");

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
                (req) -> buildAgentReportResponse(req).getAgentReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.AGENT_REPORT_EXPORT_FILE_NAME);

        log.info("exportAgentReports end");
    }

    @Override
    public void exportCurrencyReports(
            BaseReportRequest currencyReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportCurrencyReports start");

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
                (req) -> buildCurrencyReportResponse(req).getCurrencyReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.CURRENCY_REPORT_EXPORT_FILE_NAME);

        log.info("exportCurrencyReports end");
    }

    @Override
    public void exportPaymentReports(
            PaymentReportRequest paymentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportPaymentReports start");

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
                (req) -> buildPaymentReportResponse(req).getPaymentReportDtoList(),
                colRes.getDefs(),
                ExportReportDataColumns.PAYMENT_REPORT_EXPORT_FILE_NAME
        );

        log.info("exportPaymentReports end");
    }
}
