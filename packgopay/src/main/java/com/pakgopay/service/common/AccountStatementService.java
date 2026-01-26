package com.pakgopay.service.common;

import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementEditRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import jakarta.validation.Valid;

public interface AccountStatementService {

    CommonResponse queryMerchantStatements(AccountStatementQueryRequest accountStatementQueryRequest);

    CommonResponse createMerchantStatement(AccountStatementAddRequest accountStatementAddRequest);

    CommonResponse updateAccountStatement(@Valid AccountStatementEditRequest accountStatementEditRequest);
}
