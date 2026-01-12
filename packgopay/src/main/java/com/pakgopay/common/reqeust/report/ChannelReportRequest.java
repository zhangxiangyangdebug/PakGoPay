package com.pakgopay.common.reqeust.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChannelReportRequest extends BaseReportRequest{
    /** Optional: channel Id (fuzzy match) */
    @NotNull(message = "channelId is null")
    private String channelId;
}
