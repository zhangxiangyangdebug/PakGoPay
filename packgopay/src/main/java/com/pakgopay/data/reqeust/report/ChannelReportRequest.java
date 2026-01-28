package com.pakgopay.data.reqeust.report;

import lombok.Data;

@Data
public class ChannelReportRequest extends BaseReportRequest{
    /** Optional: channel Id (fuzzy match) */
    private String channelId;
}
