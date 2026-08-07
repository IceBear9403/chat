package com.social.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类。
 *
 * <p>采用双令牌机制：短效 access token（2h）+ 长效 refresh token（7d）。
 * access/refresh 使用不同密钥，refresh token 的 jti 存入 Redis 以便登出/失效控制。
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    /** 生成 access token。subject 为 userId。 */
    public static String createAccessToken(Long userId, String secret, long expireMillis, Map<String, Object> claims) {
        return buildToken(userId, secret, expireMillis, claims);
    }

    /** 生成 refresh token。subject 为 userId，claims 中带 jti（用于 Redis 失效控制）。 */
    public static String createRefreshToken(Long userId, String secret, long expireMillis, Map<String, Object> claims) {
        return buildToken(userId, secret, expireMillis, claims);
    }

    private static String buildToken(Long userId, String secret, long expireMillis, Map<String, Object> claims) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey(secret))
                .compact();
    }

    /** 解析 token，成功返回 Claims；非法或过期抛出 JwtException。 */
    public static Claims parse(String token, String secret) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String getJti(Claims claims) {
        return claims.getId();
    }

    /** HMAC-SHA256 密钥要求至少 32 字节。配置中的 secret 必须 >= 32 字符。 */
    private static SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
