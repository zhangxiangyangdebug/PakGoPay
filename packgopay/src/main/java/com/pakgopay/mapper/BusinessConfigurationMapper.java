package com.pakgopay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessConfigurationMapper {

    String getConfigValue(@Param("configKey") String configKey);

    int updateConfigValue(@Param("configKey") String configKey, @Param("configValue") String configValue);

    int insertConfig(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
