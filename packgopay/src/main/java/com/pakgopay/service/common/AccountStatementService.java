package com.pakgopay.service.common;

import com.pakgopay.data.entity.account.AdjustmentStatementRecord;
import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.dto.AccountStatementsDto;

public interface AccountStatementService {

    /**
     * Page query for statement rows. Filters now support serialNo and transactionNo.
     */
    CommonResponse queryAccountStatement(AccountStatementQueryRequest accountStatementQueryRequest);

    /**
     * Create one manual credit/debit statement and apply the real balance change in the same business flow.
     */
    CommonResponse createAccountStatement(AccountStatementAddRequest accountStatementAddRequest);

    /**
     * Persist one manual credit/debit statement row after the real balance change is already done.
     */
    void createAdjustmentStatement(AdjustmentStatementRecord payload);

    /**
     * Consume pending snapshot tasks and backfill before/after balances.
     */
    int processPendingSnapshots(int accountLimit, int statementLimit);

    /**
     * Load one statement by serialNo from its target month, with adjacent month fallback around month boundaries.
     */
    AccountStatementsDto findBySerialNo(String serialNo);
}
