package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.ExportBaseRequest;
import lombok.Data;

@Data
public class ChannelQueryRequest extends ExportBaseRequest {

    /**
     * Channel number
     */
    private String channelId;

    /**
     * paymentId number
     */
    private String paymentId;

    /**
     * Channel name
     */
    private String channelName;

    /**
     * Enabled status
     */
    private Integer status;
}
