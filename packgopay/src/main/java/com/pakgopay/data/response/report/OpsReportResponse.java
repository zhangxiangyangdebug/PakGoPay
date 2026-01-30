package com.pakgopay.data.response.report;

import lombok.Data;

import java.util.List;

@Data
public class OpsReportResponse<T> {

    /** Collection report list. */
    private List<T> collectionList;

    /** Payout report list. */
    private List<T> payoutList;
}
