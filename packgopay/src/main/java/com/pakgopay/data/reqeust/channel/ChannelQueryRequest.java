package com.pakgopay.data.reqeust.channel;

import com.pakgopay.data.reqeust.ExportBaseRequest;
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
