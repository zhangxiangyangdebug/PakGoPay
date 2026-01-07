package com.pakgopay.entity;

import com.pakgopay.common.entity.BaseReportEntity;
import lombok.Data;

@Data
public class ChannelReportEntity extends BaseReportEntity {

    /** Optional: channel name (fuzzy match) */
    private String channelName;
}
