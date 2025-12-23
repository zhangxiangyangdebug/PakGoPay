package com.pakgopay.filter;

import com.pakgopay.service.AuthorizationService;
import com.pakgopay.util.TokenUtils;
import org.apache.el.parser.Token;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token != null && AuthorizationService.verifyToken(token) != null) {
            // 设置用户认证状态
            filterChain.doFilter(request, response);
        }else if(request.getRequestURI().contains("login") || request.getRequestURI().contains("getCode") || request.getRequestURI().equals("/pakGoPay/server/heart") || request.getRequestURI().equals("/pakGoPay/server/Login/refreshToken")) {
            filterChain.doFilter(request, response);
        } else {
            // 处理无效token 重定向到登陆页
            // response.sendRedirect("/web/login");
            //filterChain.doFilter(request, response);
            //response.sendError(200, "token is expire");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");

        }
    }
}
