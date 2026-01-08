package com.pakgopay.common.reqeust.report;

import lombok.Data;

@Data
public class ChannelReportRequest extends BaseReportRequest{
    /** Optional: channel Id (fuzzy match) */
    private String channelId;
}
