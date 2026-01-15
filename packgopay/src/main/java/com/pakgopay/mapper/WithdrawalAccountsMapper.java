package com.pakgopay.mapper;

import com.pakgopay.data.entity.agent.AgentAccountInfoEntity;
import com.pakgopay.mapper.dto.WithdrawalAccountsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface WithdrawalAccountsMapper {
    /** Insert */
    int insert(WithdrawalAccountsDto dto);

    /** Find by primary key */
    Optional<WithdrawalAccountsDto> findById(@Param("id") Long id);

    /** List by merchant/agent user_id */
    List<WithdrawalAccountsDto> listByMerchantAgentId(@Param("merchantAgentId") String merchantAgentId);

    /** Update by primary key (dynamic update, only non-null fields) */
    int updateById(WithdrawalAccountsDto dto);

    /** Update status */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateTime") Long updateTime,
                     @Param("updateBy") String updateBy);

    /** Delete by primary key */
    int deleteById(@Param("id") Long id);

    /** Count by query */
    Integer countByQueryAgent(AgentAccountInfoEntity entity);

    /** UserIds by query */
    List<String> userIdsByQueryAgent(AgentAccountInfoEntity entity);

    /** Page query */
    List<WithdrawalAccountsDto> pageByQueryAgent(AgentAccountInfoEntity entity);
}
