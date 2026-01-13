package com.pakgopay.common.reqeust.channel;

import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ChannelEditRequest extends BaseRequest {

    /**
     * Channel number
     */
    @NotNull(message = "channelId is null")
    private String channelId;

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
