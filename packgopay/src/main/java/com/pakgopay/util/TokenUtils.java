package com.pakgopay.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

public class TokenUtils {
    private static final String SECRET_KEY = "wKkFKKTPpWtDq9C7cDqN8d7833T6C1xG6y9Z36ypv/lXK0cH0epj2zwaIgOGzlCdT7+ZY2GoCQWouHgDtfxPIkDxPDIVEwgqOq7yXiSLUvANubPW4tTG9MtSEskiUqEdT2YtWDTpYy8kjxSjx9L9fJ1fHq4MHwWspwvENoTyC8Q=";
    private static final long EXPIRATION_TIME = 1800000;
    private static final String TOKEN_PREFIX = "Bearer ";

    public static boolean  validateToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {
            DecodedJWT jwt = verifier.verify(token);
            jwt.getClaim("userId").asString();
            return true;
        } catch (JWTVerificationException exception) {
            return false;
        }

    }

    public static String generateToken(String username) {
        String token = "";
        LocalDateTime now = LocalDateTime.now();
        String dateTimeString = now.getYear() + String.format("%02d", now.getMonthValue()) + String.format("%02d", now.getDayOfMonth())
                + String.format("%02d", now.getHour()) + String.format("%02d", now.getMinute()) +
                String.format("%02d", now.getSecond());
        Random random = new Random();
        int randomNumber = random.nextInt(90000) + 10000;
        // 拼接token
        token = token + dateTimeString + randomNumber;
        return token;

    }

    public static String getToken(String userId) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar instance = Calendar.getInstance();
        instance.setTimeZone(tz);
        System.out.println(instance.getTime());
        // 设置过期时间
        //instance.add(Calendar.DATE, 1);
        instance.add(Calendar.MINUTE, 10);
        JWTCreator.Builder builder = JWT.create();

        return  builder.withClaim("userId", userId)
                .withExpiresAt(instance.getTime())
                .sign(algorithm);
    }

}
