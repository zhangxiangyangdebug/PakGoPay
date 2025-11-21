package com.pakgopay.filter;

import com.pakgopay.util.TokenUtils;
import org.apache.el.parser.Token;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token != null && TokenUtils.validateToken(token)) {
            // 设置用户认证状态
            System.out.println("登陆成功");
            filterChain.doFilter(request, response);
        }else if(request.getRequestURI().contains("login") || request.getRequestURI().contains("menu")) {
            System.out.println("请求的是menu");
            filterChain.doFilter(request, response);
        } else {
            // 处理无效token 重定向到登陆页
            // response.sendRedirect("/web/login");
            filterChain.doFilter(request, response);
        }
    }
}
