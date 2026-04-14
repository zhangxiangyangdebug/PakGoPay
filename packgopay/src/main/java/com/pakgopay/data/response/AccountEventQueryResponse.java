package com.pakgopay.data.response;

import lombok.Data;

import java.util.List;

@Data
public class AccountEventQueryResponse {

    private String transactionNo;
    private List<AccountEventDetailResponse> accountEvents;
}
