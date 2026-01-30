package com.pakgopay.service.impl;

import com.alibaba.fastjson.JSON;
import com.pakgopay.data.entity.report.OpsReportQueryEntity;
import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.report.OpsReportResponse;
import com.pakgopay.mapper.OpsOrderDailyMapper;
import com.pakgopay.mapper.OpsOrderMonthlyMapper;
import com.pakgopay.mapper.OpsOrderYearlyMapper;
import com.pakgopay.mapper.dto.OpsOrderDailyDto;
import com.pakgopay.mapper.dto.OpsOrderMonthlyDto;
import com.pakgopay.mapper.dto.OpsOrderYearlyDto;
import com.pakgopay.service.OpsReportService;
import com.pakgopay.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OpsReportServiceImpl implements OpsReportService {

    @Autowired
    private OpsOrderDailyMapper opsOrderDailyMapper;

    @Autowired
    private OpsOrderMonthlyMapper opsOrderMonthlyMapper;

    @Autowired
    private OpsOrderYearlyMapper opsOrderYearlyMapper;

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

}
