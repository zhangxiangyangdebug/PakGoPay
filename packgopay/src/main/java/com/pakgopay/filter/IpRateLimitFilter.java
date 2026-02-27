package com.pakgopay.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.pakgopay.service.common.RateLimitConfigService;

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT;
    private static final List<String> RATE_LIMIT_WHITELIST;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local current = redis.call('INCR', KEYS[1]) "
                        + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                        + "return current");
        INCR_EXPIRE_SCRIPT = script;
        RATE_LIMIT_WHITELIST = List.of(
                "/pakGoPay/server/notify",
                "/pakGoPay/server/heart",
                "/pakGoPay/server/menu"
        );
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitConfigService rateLimitConfigService;

    public IpRateLimitFilter(StringRedisTemplate stringRedisTemplate, RateLimitConfigService rateLimitConfigService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitConfigService = rateLimitConfigService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && isWhitelisted(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitConfigService.RateLimitConfig config = rateLimitConfigService.getConfig();
        if (config == null || !config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        long windowSeconds = config.getWindowSeconds();
        long maxRequests = config.getMaxRequests();
        if (windowSeconds <= 0 || maxRequests <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = "unknown";
        }

        Long fixedQps = config.getFixedIpQpsMap().get(clientIp);
        if (fixedQps != null && fixedQps > 0) {
            if (isLimited(clientIp, windowSeconds, fixedQps)) {
                respondRateLimited(response);
                return;
            }
        }

        if (isLimited(clientIp, windowSeconds, maxRequests)) {
            respondRateLimited(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLimited(String clientIp, long windowSeconds, long maxRequests) {
        String key = buildKey(clientIp, windowSeconds);
        Long current =
                stringRedisTemplate.execute(
                        INCR_EXPIRE_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(windowSeconds));
        return current != null && current > maxRequests;
    }

    private boolean isWhitelisted(String uri) {
        for (String path : RATE_LIMIT_WHITELIST) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }

    private void respondRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        byte[] body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}"
                .getBytes(StandardCharsets.UTF_8);
        response.getOutputStream().write(body);
    }

    private String buildKey(String clientIp, long windowSeconds) {
        long windowStart = System.currentTimeMillis() / 1000 / windowSeconds;
        return "rate:ip:" + clientIp + ":" + windowStart;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
