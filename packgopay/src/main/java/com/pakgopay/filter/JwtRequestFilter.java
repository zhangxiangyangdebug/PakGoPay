package com.pakgopay.filter;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.common.AuthorizationService.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {
    private static final String TRACE_ID = "traceId";
    //    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public JwtRequestFilter(UserDetailsService userDetailsService) {
//        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID, traceId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        String uri = request.getRequestURI();
        boolean logEnabled = isLogEnabled(uri);
        logInfo(logEnabled, "doFilterInternal, traceId: {}", MDC.get(TRACE_ID));

        try {
            if (uri != null && uri.startsWith("/pakGoPay/api/server/v1/")) {
                logInfo(logEnabled, "jwt filter skip TransactionController, uri={}", uri);
                filterChain.doFilter(request, response);
            } else if (uri != null && uri.equals("/pakGoPay/server/telegram")) {
                logInfo(logEnabled, "jwt filter skip telegram webhook, uri={}", uri);
                filterChain.doFilter(request, response);
            } else if (token == null && uri != null && uri.contains("/pakGoPay/server/notify")) {
                logInfo(logEnabled, "jwt filter skip notify auth, uri={}", request.getRequestURI());
                filterChain.doFilter(request, response);
            } else if (token != null && AuthorizationService.verifyToken(token) != null) {
                TokenClaims claims = AuthorizationService.verifyTokenClaims(token);
                if (claims == null || !StringUtils.hasText(claims.account)) {
                    logWarn(logEnabled, "jwt filter invalid token claims, uri={}", request.getRequestURI());
                    writeError(response, ResultCode.SC_UNAUTHORIZED);
                    return;
                }
                if (!matchClientInfo(request, claims)) {
                    logWarn(logEnabled, "jwt filter client info mismatch, uri={}", request.getRequestURI());
                    writeError(response, ResultCode.CLIENT_INFO_MISMATCH);
                    return;
                }
                String userId = claims.account;
                request.setAttribute(CommonConstant.ATTR_USER_ID, userId);
                String userName = claims.userName;
                request.setAttribute(CommonConstant.ATTR_USER_NAME, userName);
                logInfo(logEnabled, "jwt filter auth success, uri={}, userId={}", request.getRequestURI(), userId);
                filterChain.doFilter(request, response);
            } else if (request.getRequestURI().contains("login")
                    || request.getRequestURI().contains("getCode")
                    || request.getRequestURI().equals("/pakGoPay/server/heart")
                    || request.getRequestURI().equals("/pakGoPay/server/Login/refreshToken")) {
                logInfo(logEnabled, "jwt filter public endpoint, uri={}", request.getRequestURI());
                filterChain.doFilter(request, response);
            } else {
                // 处理无效token 重定向到登陆页
                // response.sendRedirect("/web/login");
                //filterChain.doFilter(request, response);
                //response.sendError(200, "token is expire");
                logWarn(logEnabled, "jwt filter unauthorized, uri={}", request.getRequestURI());
                writeError(response, ResultCode.SC_UNAUTHORIZED);
                //response.sendError(ResultCode.TOKEN_IS_EXPIRE.getCode(), ResultCode.TOKEN_IS_EXPIRE.getMessage());
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
            logError(logEnabled, "jwt filter error, uri={}", request.getRequestURI(), e);
        }

    }
    private boolean isLogEnabled(String uri) {
        return uri != null && uri.startsWith("/pakGoPay/");
    }

    private void logInfo(boolean enabled, String message, Object... args) {
        if (enabled) {
            log.info(message, args);
        }
    }

    private void logWarn(boolean enabled, String message, Object... args) {
        if (enabled) {
            log.warn(message, args);
        }
    }

    private void logError(boolean enabled, String message, Object arg, Throwable t) {
        if (enabled) {
            log.error(message, arg, t);
        }
    }

    private boolean matchClientInfo(HttpServletRequest request, TokenClaims claims) {
        String requestIp = resolveClientIp(request);
        String requestUa = request.getHeader("User-Agent");
        if (!StringUtils.hasText(claims.clientIp) || !StringUtils.hasText(claims.userAgent)) {
            return false;
        }
        if (!StringUtils.hasText(requestIp) || !StringUtils.hasText(requestUa)) {
            return false;
        }
        return claims.clientIp.equals(requestIp) && claims.userAgent.equals(requestUa);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, ResultCode code) throws IOException {
        int status = HttpServletResponse.SC_UNAUTHORIZED;
        if (code != null && code.getCode() != null && code.getCode() == 401) {
            status = HttpServletResponse.SC_UNAUTHORIZED;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        CommonResponse<Void> body = CommonResponse.fail(code, code.getMessage());
        response.getWriter().write(JSON.toJSONString(body));
    }
}
