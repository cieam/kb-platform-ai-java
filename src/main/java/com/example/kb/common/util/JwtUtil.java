package com.example.kb.common.util;

import com.example.kb.config.KbProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。密钥长度必须够 256 bit，否则 HS256 会直接抛异常。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(KbProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = properties.getJwt().getExpireMinutes() * 60_000L;
    }

    public String createToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败（过期、签名不对、格式错）一律抛异常，由调用方决定怎么处理。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long parseUserId(Claims claims) {
        Object value = claims.get(CLAIM_USER_ID);
        return value == null ? null : ((Number) value).longValue();
    }

    public String parseUsername(Claims claims) {
        Object value = claims.get(CLAIM_USERNAME);
        return value == null ? null : String.valueOf(value);
    }
}
