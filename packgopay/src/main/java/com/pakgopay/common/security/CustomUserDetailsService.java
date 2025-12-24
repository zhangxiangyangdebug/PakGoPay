package com.pakgopay.common.security;

import com.pakgopay.entity.User;
import com.pakgopay.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        User user =
                userMapper
                        .findRoleId(userName);

//        // 确保用户状态检查
//        if (!user.isEnabled()) {
//            throw new UsernameNotFoundException("用户已被禁用");
//        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (user.getRoleId() != null) {
            // 添加角色权限
            authorities.add(new SimpleGrantedAuthority(user.getRoleId()));

            // 添加具体权限项
//            user.getRoleId()
//                    .getRolePermissions()
//                    .forEach(
//                            rolePermission -> {
//                                authorities.add(
//                                        new SimpleGrantedAuthority(rolePermission.getPermission().getCode()));
//                            });
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUserName())
                .password(user.getPassword()) // 这里应该是数据库存储的加密密码
//                .disabled(!user.isEnabled())
//                .accountLocked(!user.isAccountNonLocked())
                .authorities(authorities)
                .build();
    }

}
