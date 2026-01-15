package com.pakgopay.service.login.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.data.entity.Children;
import com.pakgopay.data.entity.MenuItem;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.MenuItemMapper;
import com.pakgopay.mapper.RoleMapper;
import com.pakgopay.mapper.RoleMenuMapper;
import com.pakgopay.mapper.UserMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MenuItemServiceImpl {

    private static Logger logger = LogManager.getLogger("RollingFile");

    @Autowired
    private MenuItemMapper menuItemMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;
    @Autowired
    private UserMapper userMapper;

    public CommonResponse menu(String  userId) throws JsonProcessingException {
        // 根据userID查询user-role表 拿到用户角色
        Integer roleId = userMapper.getOneUserByUserId(userId).getRoleId();
        if (roleId == null) {
            return CommonResponse.fail(ResultCode.NO_ROLE_INFO_FOUND);
        }
        System.out.println("roleId = " + roleId);
        // 根据角色ID查询role-menu拿到menu
        List<String> allMenuIdsByRoleId = roleMenuMapper.getAllMenuIdsByRoleId(roleId);

        List<MenuItem> menuItems = new ArrayList<>();
        Map<String,MenuItem> menuItem = new HashMap<>();
        List<Children> children = menuItemMapper.queryMenuItem(allMenuIdsByRoleId);
        // 将所有的一级菜单放到list最外层,key用一级菜单ID
        children.stream().filter(s -> s.getParentId() == null).forEach(s -> {
           MenuItem menu = new MenuItem();
           menu.setMenuId(s.getMenuId());
           menu.setParentId(s.getParentId());
           menu.setMenuLevel(s.getMenuLevel());
           menu.setMenuName(s.getMenuName());
           menu.setNameEn(s.getNameEn());
           menu.setIcon(s.getIcon());
           menu.setUrl(s.getUrl());
           menu.setRoleMap(s.getRoleMap());
           menu.setPath(s.getPath());
           menu.setShowItem(s.isShowItem());
           menu.setMeta(s.getMeta());
           // 组装一级菜单
           menuItem.put(s.getMenuId(), menu);

        });
        // 将parentId和一级菜单ID相同的放入到对应的menu下

        List<Children> collect = children.stream().filter(a -> a.getParentId() != null).collect(Collectors.toList());
        collect.forEach(s -> {
            // 根据二级菜单parentId拿到一级菜单对象，并往其中添加二级菜单
            if (menuItem.get(s.getParentId()).getChildren() == null) {
                List<Children> child = new ArrayList<>();
                child.add(s);
                menuItem.get(s.getParentId()).setChildren(child);
            } else {
                menuItem.get(s.getParentId()).getChildren().add(s);
            }
        });
        menuItem.keySet().forEach(s -> {
            menuItems.add(menuItem.get(s));
        });
        String menuJson = null;
        try{
            menuJson = new ObjectMapper().writeValueAsString(menuItems);
        } catch (JsonProcessingException e){
            logger.error(e);
            return CommonResponse.fail(ResultCode.INTERNAL_SERVER_ERROR);
        }
        return CommonResponse.success(menuJson);
    }
}
