package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.data.entity.report.OpsReportQueryEntity;
import com.pakgopay.data.reqeust.report.OpsOrderCardRequest;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.report.OpsOrderCardResponse;
import com.pakgopay.data.response.report.OpsReportResponse;
import com.pakgopay.mapper.CollectionOrderMapper;
import com.pakgopay.mapper.OpsOrderDailyMapper;
import com.pakgopay.mapper.OpsOrderMonthlyMapper;
import com.pakgopay.mapper.OpsOrderYearlyMapper;
import com.pakgopay.mapper.PayOrderMapper;
import com.pakgopay.mapper.dto.OpsOrderDailyDto;
import com.pakgopay.mapper.dto.OpsOrderMonthlyDto;
import com.pakgopay.mapper.dto.OpsOrderYearlyDto;
import com.pakgopay.mapper.dto.MerchantReportDto;
import com.pakgopay.timer.data.ReportCurrencyRange;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.OpsReportService;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.CalcUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpsReportServiceImpl implements OpsReportService {

    @Autowired
    private OpsOrderDailyMapper opsOrderDailyMapper;

    @Autowired
    private OpsOrderMonthlyMapper opsOrderMonthlyMapper;

    @Autowired
    private OpsOrderYearlyMapper opsOrderYearlyMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private BalanceService balanceService;

    @Override
    public CommonResponse queryOpsDailyReports(OpsReportRequest request) {
        OpsReportResponse<OpsOrderDailyDto> response = new OpsReportResponse<>();
        OpsReportQueryEntity query = buildQuery(request);
        log.info("queryOpsDailyReports params={}", JSON.toJSONString(query));
        List<OpsOrderDailyDto> list = opsOrderDailyMapper.listLatest(query);
        List<OpsOrderDailyDto> collectionList = new ArrayList<>();
        List<OpsOrderDailyDto> payoutList = new ArrayList<>();
        for (OpsOrderDailyDto item : CommonUtil.safeList(list)) {
            Integer orderType = item == null ? null : item.getOrderType();
            if (orderType != null && orderType == 1) {
                payoutList.add(item);
            } else {
                collectionList.add(item);
            }
        }
        response.setCollectionList(collectionList);
        response.setPayoutList(payoutList);
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse queryOpsMonthlyReports(OpsReportRequest request) {
        OpsReportResponse<OpsOrderMonthlyDto> response = new OpsReportResponse<>();
        OpsReportQueryEntity query = buildQuery(request);
        log.info("queryOpsMonthlyReports params={}", JSON.toJSONString(query));
        List<OpsOrderMonthlyDto> list = opsOrderMonthlyMapper.listLatest(query);
        List<OpsOrderMonthlyDto> collectionList = new ArrayList<>();
        List<OpsOrderMonthlyDto> payoutList = new ArrayList<>();
        for (OpsOrderMonthlyDto item : CommonUtil.safeList(list)) {
            Integer orderType = item == null ? null : item.getOrderType();
            if (orderType != null && orderType == 1) {
                payoutList.add(item);
            } else {
                collectionList.add(item);
            }
        }
        response.setCollectionList(collectionList);
        response.setPayoutList(payoutList);
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse queryOpsYearlyReports(OpsReportRequest request) {
        OpsReportResponse<OpsOrderYearlyDto> response = new OpsReportResponse<>();
        OpsReportQueryEntity query = buildQuery(request);
        log.info("queryOpsYearlyReports params={}", JSON.toJSONString(query));
        List<OpsOrderYearlyDto> list = opsOrderYearlyMapper.listLatest(query);
        List<OpsOrderYearlyDto> collectionList = new ArrayList<>();
        List<OpsOrderYearlyDto> payoutList = new ArrayList<>();
        for (OpsOrderYearlyDto item : CommonUtil.safeList(list)) {
            Integer orderType = item == null ? null : item.getOrderType();
            if (orderType != null && orderType == 1) {
                payoutList.add(item);
            } else {
                collectionList.add(item);
            }
        }
        response.setCollectionList(collectionList);
        response.setPayoutList(payoutList);
        return CommonResponse.success(response);
    }

    @Override
    public CommonResponse queryOpsOrderCardInfo(OpsOrderCardRequest request) {
        OpsOrderCardResponse response = new OpsOrderCardResponse();
        String currency = request.getCurrency();
        Integer scopeType = request.getScopeType();
        String scopeId = request.getScopeId();
        Integer orderType = request.getOrderType();
        log.info("queryOpsOrderCardInfo params={}", JSON.toJSONString(request));

        OpsOrderCardResponse yearly = loadYearlyCard(currency, scopeId, orderType);
        OpsOrderCardResponse today = loadTodayCard(request);
        response.setOrderQuantity(safeAdd(yearly.getOrderQuantity(), today.getOrderQuantity()));
        response.setSuccessQuantity(safeAdd(yearly.getSuccessQuantity(), today.getSuccessQuantity()));
        response.setMerchantFee(CalcUtil.safeAdd(yearly.getMerchantFee(), today.getMerchantFee()));
        response.setValidMerchantFee(CalcUtil.safeAdd(yearly.getValidMerchantFee(), today.getValidMerchantFee()));
        response.setSuccessRate(CalcUtil.resolveSuccessRate(response.getSuccessQuantity(), response.getOrderQuantity()));
        response.setFrozenAmount(today.getFrozenAmount());
        response.setAvailableAmount(today.getAvailableAmount());

        return CommonResponse.success(response);
    }

    private OpsReportQueryEntity buildQuery(OpsReportRequest request) {
        OpsReportQueryEntity query = new OpsReportQueryEntity();
        if (request == null) {
            return query;
        }
        query.setRecordDate(request.getRecordDate());
        query.setCurrency(request.getCurrency());
        query.setScopeType(request.getScopeType());
        query.setScopeId(request.getScopeId());
        return query;
    }

    private OpsOrderCardResponse loadYearlyCard(String currency, String scopeId, Integer orderType) {
        OpsOrderCardResponse response = new OpsOrderCardResponse();
        ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
        LocalDate today = Instant.now().atZone(zoneId).toLocalDate();
        long todayStart = today.atStartOfDay(zoneId).toEpochSecond();
        OpsOrderDailyDto dto = opsOrderDailyMapper.sumTotalsBeforeDate(
                currency, 1, "0".equals(scopeId) ? null: scopeId, orderType, todayStart);
        long orderQuantity = dto == null || dto.getOrderQuantity() == null ? 0L : dto.getOrderQuantity();
        long successQuantity = dto == null || dto.getSuccessQuantity() == null ? 0L : dto.getSuccessQuantity();
        BigDecimal merchantFee = dto == null ? BigDecimal.ZERO : CalcUtil.defaultBigDecimal(dto.getAgentCommission());
        response.setOrderQuantity(orderQuantity);
        response.setSuccessQuantity(successQuantity);
        response.setMerchantFee(merchantFee);
        response.setValidMerchantFee(merchantFee);
        response.setSuccessRate(CalcUtil.resolveSuccessRate(successQuantity, orderQuantity));
        response.setFrozenAmount(BigDecimal.ZERO);
        response.setAvailableAmount(BigDecimal.ZERO);
        return response;
    }

    private OpsOrderCardResponse loadTodayCard(OpsOrderCardRequest request) {
        OpsOrderCardResponse response = new OpsOrderCardResponse();

        String currency = request.getCurrency();
        Integer scopeType = request.getScopeType();
        String scopeId = request.getScopeId();
        Integer orderType = request.getOrderType();

        String successStatus = TransactionStatus.SUCCESS.getCode().toString();

        if (orderType != null && orderType == 1) {
            loadStatsForToday(
                    request,
                    successStatus,
                    response,
                    (ranges, status) -> payOrderMapper.listMerchantReportStatsBatch(ranges, status),
                    (userId, c, start, end, status) ->
                            payOrderMapper.sumMerchantStatsByUserId(userId, c, start, end, status));
        } else {
            loadStatsForToday(
                    request,
                    successStatus,
                    response,
                    (ranges, status) -> collectionOrderMapper.listMerchantReportStatsBatch(ranges, status),
                    (userId, c, start, end, status) ->
                            collectionOrderMapper.sumMerchantStatsByUserId(userId, c, start, end, status));
        }

        if (response.getOrderQuantity() == null) {
            response.setOrderQuantity(0L);
        }
        if (response.getSuccessQuantity() == null) {
            response.setSuccessQuantity(0L);
        }
        if (response.getMerchantFee() == null) {
            response.setMerchantFee(BigDecimal.ZERO);
        }
        response.setValidMerchantFee(response.getMerchantFee());
        response.setSuccessRate(CalcUtil.resolveSuccessRate(response.getSuccessQuantity(), response.getOrderQuantity()));
        applyBalanceInfo(response, currency, scopeType, scopeId);
        return response;
    }

    private void loadStatsForToday(OpsOrderCardRequest request,
                                          String successStatus,
                                          OpsOrderCardResponse response,
                                          BatchStatsLoader batchLoader,
                                          UserStatsLoader userLoader) {
        String currency = request.getCurrency();
        Integer scopeType = request.getScopeType();
        String scopeId = request.getScopeId();
        ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
        LocalDate today = Instant.now().atZone(zoneId).toLocalDate();
        long startTime = today.atStartOfDay(zoneId).toEpochSecond();
        long endTime = today.plusDays(1).atStartOfDay(zoneId).toEpochSecond();
        if (scopeType == 0) {
            List<ReportCurrencyRange> ranges = Collections.singletonList(
                    new ReportCurrencyRange(currency, startTime, endTime));
            try {
                applyTotals(response, batchLoader.load(ranges, successStatus));
            } catch (Exception e) {
                log.error("queryOpsOrderCardInfo load stats failed, message={}", e.getMessage());
            }
            return;
        }
        if (scopeType == 1) {
            try {
                MerchantReportDto dto = userLoader.load(scopeId, currency, startTime, endTime, successStatus);
                applyTotals(response, dto == null ? Collections.emptyList() : Collections.singletonList(dto));
            } catch (Exception e) {
                log.error("queryOpsOrderCardInfo load stats failed, message={}", e.getMessage());
            }
            return;
        }
        return;
    }

    @FunctionalInterface
    private interface BatchStatsLoader {
        List<MerchantReportDto> load(List<ReportCurrencyRange> ranges, String successStatus);
    }

    @FunctionalInterface
    private interface UserStatsLoader {
        MerchantReportDto load(String userId, String currency, long startTime, long endTime, String successStatus);
    }

    private void applyTotals(OpsOrderCardResponse response, List<MerchantReportDto> stats) {
        long orderQuantity = response.getOrderQuantity() == null ? 0L : response.getOrderQuantity();
        long successQuantity = response.getSuccessQuantity() == null ? 0L : response.getSuccessQuantity();
        BigDecimal merchantFee = response.getMerchantFee() == null ? BigDecimal.ZERO : response.getMerchantFee();
        for (MerchantReportDto dto : CommonUtil.safeList(stats)) {
            orderQuantity += dto.getOrderQuantity() == null ? 0L : dto.getOrderQuantity();
            successQuantity += dto.getSuccessQuantity() == null ? 0L : dto.getSuccessQuantity();
            merchantFee = CalcUtil.safeAdd(merchantFee, dto.getMerchantFee());
        }
        response.setOrderQuantity(orderQuantity);
        response.setSuccessQuantity(successQuantity);
        response.setMerchantFee(merchantFee);
    }

    private void applyBalanceInfo(OpsOrderCardResponse response, String currency, Integer scopeType, String userId) {
        try {
            List<String> userIds = scopeType == 1
                    ? Collections.singletonList(userId)
                    : Collections.emptyList();
            Map<String, Map<String, BigDecimal>> totalData =
                    balanceService.fetchBalanceSummaries(userIds).getTotalData();
            Map<String, BigDecimal> currencyBalance =
                    totalData == null ? Collections.emptyMap() : totalData.getOrDefault(currency, Collections.emptyMap());
            response.setFrozenAmount(currencyBalance.getOrDefault("frozen", BigDecimal.ZERO));
            response.setAvailableAmount(currencyBalance.getOrDefault("available", BigDecimal.ZERO));
        } catch (Exception e) {
            log.error("queryOpsOrderCardInfo load balance failed, message={}", e.getMessage());
            response.setFrozenAmount(BigDecimal.ZERO);
            response.setAvailableAmount(BigDecimal.ZERO);
        }
    }

    private long safeAdd(Long left, Long right) {
        return (left == null ? 0L : left) + (right == null ? 0L : right);
    }
}
