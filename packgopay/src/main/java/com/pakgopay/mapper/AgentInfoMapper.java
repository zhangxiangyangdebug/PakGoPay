package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.AgentInfoDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentInfoMapper {

    /**
     * xiaoyou 通过 userId 查询代理（商户）全部信息
     */
    AgentInfoDto findByUserId(@Param("userId") String userId);

    /**
     * xiaoyou 查询所有代理信息
     */
    List<AgentInfoDto> getAllAgentInfo();
}
