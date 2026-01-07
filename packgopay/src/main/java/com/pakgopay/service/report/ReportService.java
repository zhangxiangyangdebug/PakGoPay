package com.pakgopay.service.report;

import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.report.MerchantReportRequest;
import com.pakgopay.common.response.CommonResponse;

public interface ReportService {


    CommonResponse queryMerchantReport(MerchantReportRequest merchantReportRequest) throws PakGoPayException;
}
