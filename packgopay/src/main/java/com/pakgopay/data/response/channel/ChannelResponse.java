package com.pakgopay.data.response.channel;

import com.pakgopay.mapper.dto.ChannelDto;
import lombok.Data;

import java.util.List;

@Data
public class ChannelResponse {

    private List<ChannelDto> channelDtoList;

    /**
     * page no
     */
    private Integer pageNo;

    /**
     * page size
     */
    private Integer pageSize;


    /**
     * total number
     */
    private Integer totalNumber;
}
