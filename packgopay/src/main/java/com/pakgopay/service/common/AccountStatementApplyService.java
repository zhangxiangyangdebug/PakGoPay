package com.pakgopay.service.common;

import com.pakgopay.mapper.dto.AccountStatementEnqueueDto;

import java.util.List;

public interface AccountStatementApplyService {

    void enqueuePendingApplyRows(List<AccountStatementEnqueueDto> rows);

    int processPendingApplies(int accountLimit, int statementLimit);
}
