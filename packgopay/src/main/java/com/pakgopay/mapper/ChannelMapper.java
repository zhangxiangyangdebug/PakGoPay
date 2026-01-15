package com.pakgopay.mapper;

import com.pakgopay.data.entity.channel.ChannelEntity;
import com.pakgopay.mapper.dto.ChannelDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChannelMapper {

    ChannelDto findByChannelId(@Param("channelId") Long channelId);

    List<ChannelDto> getAllChannels();

    int insert(ChannelDto dto);

    int updateByChannelId(ChannelDto dto);

    int deleteByChannelId(@Param("channelId") Long channelId);

    List<ChannelDto> getPaymentIdsByChannelIds(@Param("channelIds") List<Long> channelIds, @Param("status") Integer status);

    /** Count by query */
    Integer countByQuery(ChannelEntity entity);

    /** Page query */
    List<ChannelDto> pageByQuery(ChannelEntity entity);
}
