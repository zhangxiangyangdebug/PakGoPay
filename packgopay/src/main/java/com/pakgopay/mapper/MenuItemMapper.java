package com.pakgopay.mapper;

import com.pakgopay.common.entity.Children;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MenuItemMapper {
    List<Children> queryMenuItem(@Param(value="menuIds") List<String> allMenuIdsByRoleId);
}
