package com.pakgopay.service;

import com.pakgopay.data.reqeust.bankCode.BankCodeQueryRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeUpdateRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface BankCodeService {
    CommonResponse queryBankCode(BankCodeQueryRequest request);

    CommonResponse queryPaymentBankCode(PaymentBankCodeQueryRequest request);

    CommonResponse updatePaymentBankCodes(PaymentBankCodeUpdateRequest request);
}
