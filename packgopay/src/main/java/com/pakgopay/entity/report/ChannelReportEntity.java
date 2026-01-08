package com.pakgopay.entity.report;

import lombok.Data;

@Data
public class ChannelReportEntity extends BaseReportEntity {

    /** Optional: channel Id (fuzzy match) */
    private String channelId;

    /** Optional: order type */
    private Integer orderType;
}
