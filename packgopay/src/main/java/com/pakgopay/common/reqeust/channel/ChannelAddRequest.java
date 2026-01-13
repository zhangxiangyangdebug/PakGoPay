package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

import java.util.List;

@Data
public class ChannelAddRequest extends BaseRequest {

    /**
     * Channel name
     */
    private String channelName;

    /**
     * Enabled status
     */
    private Integer status;

    /**
     * payment ids
     */
    private List<Integer> paymentIds;
}
