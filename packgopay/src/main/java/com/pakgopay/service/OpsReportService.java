package com.pakgopay.service;

import com.pakgopay.data.reqeust.report.OpsReportRequest;
import com.pakgopay.data.reqeust.report.OpsOrderCardRequest;
import com.pakgopay.data.response.CommonResponse;

public interface OpsReportService {

    CommonResponse queryOpsDailyReports(OpsReportRequest request);

    CommonResponse queryOpsMonthlyReports(OpsReportRequest request);

    CommonResponse queryOpsYearlyReports(OpsReportRequest request);

    CommonResponse queryOpsOrderCardInfo(OpsOrderCardRequest request);
}
