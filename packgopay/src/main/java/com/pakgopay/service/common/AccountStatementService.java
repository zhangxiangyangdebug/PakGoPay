package com.pakgopay.service.common;

import com.pakgopay.data.entity.account.AdjustmentStatementRecord;
import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementEditRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;

public interface AccountStatementService {

    CommonResponse queryAccountStatement(AccountStatementQueryRequest accountStatementQueryRequest);

    CommonResponse createAccountStatement(AccountStatementAddRequest accountStatementAddRequest);

    CommonResponse updateAccountStatement(AccountStatementEditRequest accountStatementEditRequest);

    /**
     * Persist one adjustment statement row (order_type=3) with before/after balance snapshots.
     */
    void createAdjustmentStatement(AdjustmentStatementRecord payload);
}
