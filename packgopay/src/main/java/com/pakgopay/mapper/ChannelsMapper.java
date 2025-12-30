package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.ChannelsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChannelsMapper {

    ChannelsDto findByChannelId(@Param("channelId") Long channelId);

    List<ChannelsDto> getAllChannels();

    int insert(ChannelsDto dto);

    int updateByChannelId(ChannelsDto dto);

    int deleteByChannelId(@Param("channelId") Long channelId);

    List<ChannelsDto> getPaymentIdsByChannelIds(@Param("channelIds") List<Long> channelIds, @Param("status") Integer status);
}
