package com.pakgopay.data.reqeust.channel;

import com.pakgopay.data.reqeust.BaseRequest;
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
     * remark
     */
    private String remark;

    /**
     * payment ids
     */
    private List<Integer> paymentIds;
}
