package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.ConfigurationDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface ConfigurationMapper {

    Optional<ConfigurationDto> findByUserId(String userId);

    void save(ConfigurationDto configurationDto);
}
