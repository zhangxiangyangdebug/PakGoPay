package com.pakgopay.service.common;

import com.pakgopay.mapper.LoginLogMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.LoginLogDto;
import com.pakgopay.mapper.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Slf4j
@Service
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;
    private final UserMapper userMapper;

    public LoginLogService(LoginLogMapper loginLogMapper,
                           UserMapper userMapper) {
        this.loginLogMapper = loginLogMapper;
        this.userMapper = userMapper;
    }

    public void writeLogin(UserDTO user, String loginIp, String tokenJti) {
        long now = Instant.now().getEpochSecond();
        // Login event log.
        write(user, loginIp, 1, now, tokenJti, null);
    }

    public void writeManualLogout(UserDTO user, String loginIp, String tokenJti) {
        long now = Instant.now().getEpochSecond();
        // Manual logout event log.
        write(user, loginIp, 2, now, tokenJti, "manual");
    }

    public void writeKickedLogout(String userId, String operatorId) {
        try {
            UserDTO user = userMapper.getOneUserByUserId(userId);
            if (user == null) {
                log.warn("writeKickedLogout skip, user not found, userId={}, operatorId={}", userId, operatorId);
                return;
            }
            long now = Instant.now().getEpochSecond();
            String reason = StringUtils.hasText(operatorId) ? "kicked:" + operatorId : "kicked";
            write(user, null, 2, now, null, reason);
            log.info("kicked logout logged, userId={}, reason={}", userId, reason);
        } catch (Exception e) {
            log.warn("writeKickedLogout failed, userId={}, operatorId={}, message={}", userId, operatorId, e.getMessage());
        }
    }

    public void writeRefreshTokenExpired(String refreshToken, String fallbackIp) {
        try {
            AuthorizationService.TokenClaims claims = AuthorizationService.parseTokenClaimsWithoutVerification(refreshToken);
            if (claims == null || !StringUtils.hasText(claims.account)) {
                log.warn("writeRefreshTokenExpired skip, claims invalid");
                return;
            }
            if (StringUtils.hasText(claims.jti)) {
                Integer existed = loginLogMapper.countByTokenJtiAndEventType(claims.jti, 2);
                if (existed != null && existed > 0) {
                    log.info("writeRefreshTokenExpired skip duplicate, tokenJti={}", claims.jti);
                    return;
                }
            }
            UserDTO user = userMapper.getOneUserByUserId(claims.account);
            if (user == null) {
                log.warn("writeRefreshTokenExpired skip, user not found, account={}", claims.account);
                return;
            }
            long now = Instant.now().getEpochSecond();
            write(user, StringUtils.hasText(fallbackIp) ? fallbackIp : claims.clientIp, 2, now, claims.jti, "expired");
            log.info("refresh token expired logout logged, userId={}, tokenJti={}", user.getUserId(), claims.jti);
        } catch (Exception e) {
            log.warn("writeRefreshTokenExpired failed, message={}", e.getMessage());
        }
    }

    private void write(UserDTO user,
                       String loginIp,
                       Integer eventType,
                       Long eventTime,
                       String tokenJti,
                       String logoutReason) {
        if (user == null || !StringUtils.hasText(user.getUserId())) {
            log.warn("write login log skip, invalid user");
            return;
        }
        try {
            LoginLogDto dto = new LoginLogDto();
            dto.setUserId(user.getUserId());
            dto.setLoginName(user.getLoginName());
            dto.setLoginRole(user.getRoleName());
            dto.setLoginIp(loginIp);
            dto.setCreateTime(eventTime);
            dto.setUpdateTime(eventTime);
            dto.setEventType(eventType);
            dto.setEventTime(eventTime);
            dto.setTokenJti(tokenJti);
            dto.setLogoutReason(logoutReason);
            loginLogMapper.insert(dto);
            log.info("login log inserted, dto={}", dto);
        } catch (Exception e) {
            log.error("write login log failed, userId={}, eventType={}, message={}",
                    user.getUserId(), eventType, e.getMessage());
        }
    }
}
