package com.pakgopay.filter;

import com.alibaba.fastjson.JSON;
import com.pakgopay.common.constant.GoogleCodeProtectedEndpoints;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.common.AuthorizationService.TokenClaims;
import com.pakgopay.thirdUtil.GoogleUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;

@Component
public class GoogleCodeFilter extends OncePerRequestFilter {
    private final UserMapper userMapper;

    public GoogleCodeFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (!GoogleCodeProtectedEndpoints.requiresGoogleCode(method, uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(request);

        Long googleCode = resolveGoogleCode(wrapper);
        if (googleCode == null) {
            writeError(response, ResultCode.INVALID_PARAMS, "googleCode is required");
            return;
        }

        String token = extractToken(wrapper);
        if (!StringUtils.hasText(token)) {
            writeError(response, ResultCode.INVALID_TOKEN, ResultCode.INVALID_TOKEN.getMessage());
            return;
        }

        TokenClaims claims = AuthorizationService.verifyTokenClaims(token);
        if (claims == null || !StringUtils.hasText(claims.account)) {
            writeError(response, ResultCode.INVALID_TOKEN, ResultCode.INVALID_TOKEN.getMessage());
            return;
        }

        String userId = claims.account;
        String secretKey = userMapper.getSecretKeyByUserId(userId);
        if (!StringUtils.hasText(secretKey)) {
            writeError(response, ResultCode.BIND_SECRET_KEY_FAIL, ResultCode.BIND_SECRET_KEY_FAIL.getMessage());
            return;
        }

        if (!GoogleUtil.verifyQrCode(secretKey, googleCode)) {
            writeError(response, ResultCode.CODE_IS_EXPIRE, ResultCode.CODE_IS_EXPIRE.getMessage());
            return;
        }

        filterChain.doFilter(wrapper, response);
    }

    private Long resolveGoogleCode(CachedBodyHttpServletRequest request) throws IOException {
        String param = request.getParameter("googleCode");
        if (StringUtils.hasText(param)) {
            return parseLong(param);
        }

        String body = readBody(request);
        if (!StringUtils.hasText(body)) {
            return null;
        }

        try {
            Map<String, Object> json = JSON.parseObject(body, Map.class);
            Object value = json == null ? null : json.get("googleCode");
            if (value == null) {
                return null;
            }
            return parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private String readBody(CachedBodyHttpServletRequest request) {
        byte[] buf = request.getCachedBody();
        return buf.length == 0 ? "" : new String(buf, StandardCharsets.UTF_8);
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

    private void writeError(HttpServletResponse response, ResultCode code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        CommonResponse<Void> body = CommonResponse.fail(code, message);
        response.getWriter().write(JSON.toJSONString(body));
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            final ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return buffer.read();
                }

                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
