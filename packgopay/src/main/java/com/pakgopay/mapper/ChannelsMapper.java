package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.ChannelDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChannelsMapper {

    ChannelDto findByChannelId(@Param("channelId") Long channelId);

    List<ChannelDto> getAllChannels();

    int insert(ChannelDto dto);

    int updateByChannelId(ChannelDto dto);

    int deleteByChannelId(@Param("channelId") Long channelId);

    List<ChannelDto> getPaymentIdsByChannelIds(@Param("channelIds") List<Long> channelIds, @Param("status") Integer status);
}
