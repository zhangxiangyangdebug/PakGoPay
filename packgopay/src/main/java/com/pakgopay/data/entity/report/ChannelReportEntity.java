package com.pakgopay.data.entity.report;

import lombok.Data;

@Data
public class ChannelReportEntity extends BaseReportEntity {

    /** Optional: channel Id (fuzzy match) */
    private Long channelId;
}
