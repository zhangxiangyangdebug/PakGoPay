package com.pakgopay.service.report.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.*;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.common.response.report.*;
import com.pakgopay.entity.report.*;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.balance.BalanceService;
import com.pakgopay.service.common.CommonService;
import com.pakgopay.service.report.ExportReportDataColumns;
import com.pakgopay.service.report.ReportService;
import com.pakgopay.service.report.ThrowingFunction;
import com.pakgopay.util.CommontUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        ColumnParseResult<MerchantReportDto> colRes =
                parseColumns(merchantReportRequest, ExportReportDataColumns.MERCHANT_ALLOWED);

        // 2) Set Excel download response headers
        setExcelDownloadHeaders(response, CommonConstant.MERCHANT_EXPORT_FILE_NAME);

        // 3) Init paging params
        merchantReportRequest.setPageSize(CommonConstant.EXPORT_PAGE_SIZE);
        merchantReportRequest.setPageNo(1);

        // 4) Export by paging and multi-sheet writing
        exportByPagingAndSheets(
                response,
                colRes.getHead(),
                merchantReportRequest,
                (req) -> getMerchantReportResponse(req).getMerchantReportDtoList(),
                colRes.getDefs()
        );

        log.info("exportMerchantReport end");
    }

    @Override
    public void exportChannelReport(
            ChannelReportRequest channelReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportChannelReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ColumnParseResult<ChannelReportDto> colRes =
                parseColumns(channelReportRequest, ExportReportDataColumns.CHANNEL_ALLOWED);

        // 2) Set Excel download response headers
        setExcelDownloadHeaders(response, CommonConstant.CHANNEL_EXPORT_FILE_NAME);

        // 3) Init paging params
        channelReportRequest.setPageSize(CommonConstant.EXPORT_PAGE_SIZE);
        channelReportRequest.setPageNo(1);

        // 4) Export by paging and multi-sheet writing
        exportByPagingAndSheets(
                response,
                colRes.getHead(),
                channelReportRequest,
                (req) -> getChannelReportResponse(req).getChannelReportDtoList(),
                colRes.getDefs()
        );

        log.info("exportChannelReport end");

    }

    @Override
    public void exportAgentReport(
            AgentReportRequest agentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportAgentReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ColumnParseResult<AgentReportDto> colRes =
                parseColumns(agentReportRequest, ExportReportDataColumns.AGENT_ALLOWED);

        // 2) Set Excel download response headers
        setExcelDownloadHeaders(response, CommonConstant.AGENT_EXPORT_FILE_NAME);

        // 3) Init paging params
        agentReportRequest.setPageSize(CommonConstant.EXPORT_PAGE_SIZE);
        agentReportRequest.setPageNo(1);

        // 4) Export by paging and multi-sheet writing
        exportByPagingAndSheets(
                response,
                colRes.getHead(),
                agentReportRequest,
                (req) -> getAgentReportResponse(req).getAgentReportDtoList(),
                colRes.getDefs()
        );

        log.info("exportAgentReport end");
    }

    @Override
    public void exportCurrencyReport(
            BaseReportRequest currencyReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportCurrencyReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ColumnParseResult<CurrencyReportDto> colRes =
                parseColumns(currencyReportRequest, ExportReportDataColumns.CURRENCY_ALLOWED);

        // 2) Set Excel download response headers
        setExcelDownloadHeaders(response, CommonConstant.CURRENCY_EXPORT_FILE_NAME);

        // 3) Init paging params
        currencyReportRequest.setPageSize(CommonConstant.EXPORT_PAGE_SIZE);
        currencyReportRequest.setPageNo(1);

        // 4) Export by paging and multi-sheet writing
        exportByPagingAndSheets(
                response,
                colRes.getHead(),
                currencyReportRequest,
                (req) -> getCurrencyReportResponse(req).getCurrencyReportDtoList(),
                colRes.getDefs()
        );

        log.info("exportCurrencyReport end");
    }

    @Override
    public void exportPaymentReport(
            PaymentReportRequest paymentReportRequest, HttpServletResponse response)
            throws PakGoPayException, IOException {
        log.info("exportPaymentReport start");

        // 1) Parse and validate export columns (must go through whitelist)
        ColumnParseResult<PaymentReportDto> colRes =
                parseColumns(paymentReportRequest, ExportReportDataColumns.PAYMENT_ALLOWED);

        // 2) Set Excel download response headers
        setExcelDownloadHeaders(response, CommonConstant.PAYMENT_EXPORT_FILE_NAME);

        // 3) Init paging params
        paymentReportRequest.setPageSize(CommonConstant.EXPORT_PAGE_SIZE);
        paymentReportRequest.setPageNo(1);

        // 4) Export by paging and multi-sheet writing
        exportByPagingAndSheets(
                response,
                colRes.getHead(),
                paymentReportRequest,
                (req) -> getPaymentReportResponse(req).getPaymentReportDtoList(),
                colRes.getDefs()
        );

        log.info("exportPaymentReport end");
    }

    /**
     * Core export logic: paging query + multi-sheet writing
     */
    private <REQ extends BaseReportRequest, ROW> void exportByPagingAndSheets(
            HttpServletResponse response,
            List<List<String>> head,
            REQ request,
            ThrowingFunction<REQ, List<ROW>, PakGoPayException> pageFetcher,
            List<ExportReportDataColumns.ColumnDef<ROW>> defs)
            throws IOException, PakGoPayException {

        int sheetNo = 1;           // Current sheet index (start from 1)
        int sheetRowCount = 0;     // Current written row count in current sheet
        boolean wroteAny = false;  // Whether any data has been written

        try (var os = response.getOutputStream();
             var writer = EasyExcel.write(os)
                     .head(head)
                     .autoCloseStream(false)
                     .build()) {

            while (true) {
                // 1) Fetch one page data
                List<ROW> pageData = pageFetcher.apply(request);

                // 2) Stop if no more data
                if (pageData == null || pageData.isEmpty()) {
                    break;
                }
                wroteAny = true;

                // 3) Convert DTO list to EasyExcel dynamic rows
                List<List<String>> excelRows = toDynamicRows(pageData, defs);

                // 4) Switch to next sheet if current sheet capacity is not enough
                if (sheetRowCount + excelRows.size() > CommonConstant.EXPORT_SHEET_ROW_LIMIT) {
                    sheetNo++;
                    sheetRowCount = 0;
                }

                // 5) Write to current sheet
                writer.write(excelRows, EasyExcel.writerSheet(sheetNo, "report-" + sheetNo).build());

                // 6) Update current sheet row count
                sheetRowCount += excelRows.size();

                // 7) If current page size is less than page size, it means this is the last page
                if (excelRows.size() < CommonConstant.EXPORT_PAGE_SIZE) {
                    break;
                }

                // 8) Move to next page
                request.setPageNo(request.getPageNo() + 1);
            }
        }

        // 9) If no data has been written, treat as empty result
        if (!wroteAny) {
            log.warn("data is empty");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "data is empty");
        }
    }

    /**
     * Set Excel download headers
     */
    private void setExcelDownloadHeaders(HttpServletResponse response, String fileName) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /**
     * Parse and validate merchant export columns (must use whitelist)
     */
    private <ROW> ColumnParseResult<ROW> parseColumns(
            BaseReportRequest req,
            Map<String, ExportReportDataColumns.ColumnDef<ROW>> allowedMap)
            throws PakGoPayException {

        // 1) Validate columns
        if (req.getColumns() == null || req.getColumns().isEmpty()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "columns is empty");
        }

        // 2) Parse columns by frontend order
        List<ExportReportDataColumns.ColumnDef<ROW>> defs = new ArrayList<>();
        List<List<String>> head = new ArrayList<>();

        for (BaseReportRequest.ExportCol col : req.getColumns()) {
            var def = allowedMap.get(col.getKey());
            if (def == null) {
                throw new PakGoPayException(ResultCode.INVALID_PARAMS, "not support column: " + col.getKey());
            }

            defs.add(def);

            // Use frontend title first, otherwise fallback to backend default title
            String title = (col.getTitle() != null && !col.getTitle().isBlank())
                    ? col.getTitle()
                    : def.defaultTitle();

            // EasyExcel dynamic header format: List<List<String>>
            head.add(Collections.singletonList(title));
        }

        return new ColumnParseResult<>(defs, head);
    }

    /**
     * Convert DTO list to EasyExcel dynamic row format (List<List<String>>)
     */
    private <ROW> List<List<String>> toDynamicRows(
            List<ROW> list,
            List<ExportReportDataColumns.ColumnDef<ROW>> defs) {

        return list.stream()
                .map(r -> defs.stream()
                        .map(d -> d.getter().apply(r))
                        .toList())
                .toList();
    }


    /**
     * Column parse result holder
     */
    private static class ColumnParseResult<T> {
        private final List<ExportReportDataColumns.ColumnDef<T>> defs;
        private final List<List<String>> head;

        public ColumnParseResult(List<ExportReportDataColumns.ColumnDef<T>> defs, List<List<String>> head) {
            this.defs = defs;
            this.head = head;
        }

        public List<ExportReportDataColumns.ColumnDef<T>> getDefs() {
            return defs;
        }

        public List<List<String>> getHead() {
            return head;
        }
    }
}
