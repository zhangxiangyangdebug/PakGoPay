package com.pakgopay.service;

import com.pakgopay.data.reqeust.account.WithdrawOrderAuditRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderCreateRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface WithdrawOrderService {

    CommonResponse queryWithdrawOrderPage(WithdrawOrderQueryRequest request);

    CommonResponse createWithdrawOrder(WithdrawOrderCreateRequest request);

    CommonResponse auditWithdrawOrder(WithdrawOrderAuditRequest request);
}
