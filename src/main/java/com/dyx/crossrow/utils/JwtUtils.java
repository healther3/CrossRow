package com.dyx.crossrow.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private static final String SECRET_STRING = "CrossRowSuperSecretKeyForJwtAuthentication2026!";

    // generate secret key by encoding
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    // 24 hours expiration
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24;

    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)                 // 设置主题（通常放用户名）
                .claim("userId", userId)           // 核心：把 userId 塞进 Token 里，以后非常方便获取
                .issuedAt(new Date())              // 签发时间
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 过期时间
                .signWith(SECRET_KEY)              // 签名
                .compact();
    }
}
