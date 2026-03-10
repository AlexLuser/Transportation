package com.fm.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 功能：
 * 1. 生成Token（登录时使用）
 * 2. 解析Token（获取用户信息）
 * 3. 验证Token（网关验证时使用）
 */
@Component
public class JwtUtil {

    /**
     * JWT密钥（从配置文件读取，默认值用于开发环境）
     */
    @Value("${jwt.secret:TransportationSystemSecretKeyForJWTTokenGeneration2024}")
    private String secret;

    /**
     * Token过期时间（秒），默认2小时
     */
    @Value("${jwt.expiration:7200}")
    private Long expiration;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Token
     * 功能：根据用户信息生成JWT Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param roleCode 角色编码（admin/customer/shop/driver）
     * @return Token字符串
     */
    public String generateToken(Long userId, String username, String roleCode) {
        // 构建Token的载荷（Payload）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("roleCode", roleCode);

        // 计算过期时间
        Date expirationDate = new Date(System.currentTimeMillis() + expiration * 1000);

        // 生成Token
        return Jwts.builder()
                .setClaims(claims)  // 设置载荷
                .setSubject(username)  // 设置主题（用户名）
                .setIssuedAt(new Date())  // 设置签发时间
                .setExpiration(expirationDate)  // 设置过期时间
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)  // 签名
                .compact();
    }

    /**
     * 从Token中获取Claims（载荷）
     * 功能：解析Token，获取所有信息
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     * 功能：解析Token，提取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId == null) {
            return null;
        }
        // 处理不同类型（可能是Integer或Long）
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof Long) {
            return (Long) userId;
        } else if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    /**
     * 从Token中获取用户名
     * 功能：解析Token，提取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从Token中获取角色编码
     * 功能：解析Token，提取角色信息
     */
    public String getRoleCodeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? (String) claims.get("roleCode") : null;
    }

    /**
     * 验证Token是否有效
     * 功能：检查Token是否过期、格式是否正确
     * 使用场景：网关拦截请求时验证Token
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断Token是否过期
     * 功能：检查Token的过期时间
     */
    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 获取Token过期时间（秒）
     * 功能：返回配置的过期时间，用于前端显示
     */
    public Long getExpiration() {
        return expiration;
    }
}
