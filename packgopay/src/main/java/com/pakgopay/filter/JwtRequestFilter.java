package com.pakgopay.filter;

import com.pakgopay.service.common.AuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {
    //    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public JwtRequestFilter(UserDetailsService userDetailsService) {
//        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        System.out.println("'lsa11111'");
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;

        try {
            if (token != null && AuthorizationService.verifyToken(token) != null) {
                // 设置用户认证状态
                filterChain.doFilter(request, response);
            } else if (request.getRequestURI().contains("login") || request.getRequestURI().contains("getCode")
                    || request.getRequestURI().equals("/pakGoPay/server/heart")
                    || request.getRequestURI().equals("/pakGoPay/server/Login/refreshToken")) {
                filterChain.doFilter(request, response);
            } else {
                // 处理无效token 重定向到登陆页
                // response.sendRedirect("/web/login");
                //filterChain.doFilter(request, response);
                //response.sendError(200, "token is expire");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");

            }
            String userName = null;

//            if (jwt != null && jwtUtils.validateToken(jwt)) {
//                userName = jwtUtils.getUsernameFromToken(jwt);
//                UserDetails userDetails = userDetailsService.loadUserByUsername(userName);
//
//                UsernamePasswordAuthenticationToken authentication =
//                        new UsernamePasswordAuthenticationToken(
//                                userDetails, null, userDetails.getAuthorities());
//
//                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//                SecurityContextHolder.getContext().setAuthentication(authentication);
//            }
        } catch (Exception e) {
//            log.error("认证过程异常");
        }

    }
}
