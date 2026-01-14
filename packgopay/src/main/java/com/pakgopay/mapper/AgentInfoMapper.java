package com.pakgopay.mapper;

import com.pakgopay.entity.agent.AgentInfoEntity;
import com.pakgopay.mapper.dto.AgentInfoDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentInfoMapper {


    /** Query agent info by login userId */
    AgentInfoDto findByUserId(@Param("userId") String userId);

    /** Insert agent info */
    int insert(AgentInfoDto dto);

    int updateByUserId(AgentInfoDto dto);

    /**
     * xiaoyou 查询所有代理信息
     */
    List<AgentInfoDto> getAllAgentInfo();

    /**
     * Update status by userId
     * @return affected rows
     */
    int updateStatus(@Param("userId") String userId,
                     @Param("status") Integer status);

    /** Count by query */
    Integer countByQuery(AgentInfoEntity entity);

    /** Page query */
    List<AgentInfoDto> pageByQuery(AgentInfoEntity entity);
}
