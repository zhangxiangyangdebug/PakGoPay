package com.pakgopay.service.common;

import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface AccountStatementService {

    CommonResponse queryMerchantRecharge(AccountStatementQueryRequest accountStatementQueryRequest);

    CommonResponse addMerchantRecharge(AccountStatementAddRequest accountStatementAddRequest);

}
