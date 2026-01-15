package com.pakgopay.data.response.report;

import com.pakgopay.mapper.dto.ChannelReportDto;
import lombok.Data;

import java.util.List;

@Data
public class ChannelReportResponse extends BaseReportResponse{
    /**
     * merchant report info list
     */
    private List<ChannelReportDto> channelReportDtoList;
}
