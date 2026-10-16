package com.aaf.link.auth.token;

import com.aaf.link.auth.config.JwtProperties;
import com.aaf.link.auth.model.dto.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * 令牌服务：签发的 Access / Refresh 双令牌共用同一密钥，用 {@code type} 声明区分用途。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code jti}（令牌唯一标识）：登出时用它做失效名单的 key，避免拿整个令牌（200+ 字节）当 key 浪费 Redis 内存</li>
 *   <li>{@code tenantId} 声明：租户标识的唯一可信来源，客户端传什么都不信</li>
 *   <li>解析失败一律返回 null / false，不向上抛异常，由调用方按"未认证"处理</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    // ==================== 签发 ====================

    public String generateAccessToken(LoginUser user) {
        return buildToken(user, TYPE_ACCESS, jwtProperties.getExpiration());
    }

    public String generateRefreshToken(LoginUser user) {
        return buildToken(user, TYPE_REFRESH, jwtProperties.getRefreshExpiration());
    }

    private String buildToken(LoginUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TENANT_ID, user.getTenantId())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey())
                .compact();
    }

    // ==================== 校验 ====================

    public boolean validateAccessToken(String token) {
        return TYPE_ACCESS.equals(readType(token));
    }

    public boolean validateRefreshToken(String token) {
        return TYPE_REFRESH.equals(readType(token));
    }

    // ==================== 声明读取 ====================

    public String getUsername(String token) {
        return readClaim(token, Claims::getSubject);
    }

    public Long getUserId(String token) {
        return readClaim(token, claims -> {
            Number value = claims.get(CLAIM_USER_ID, Number.class);
            return value == null ? null : value.longValue();
        });
    }

    public Long getTenantId(String token) {
        return readClaim(token, claims -> {
            Number value = claims.get(CLAIM_TENANT_ID, Number.class);
            return value == null ? null : value.longValue();
        });
    }

    /** 令牌唯一标识（jti），失效名单的 key */
    public String getTokenId(String token) {
        return readClaim(token, Claims::getId);
    }

    /**
     * 剩余有效期（毫秒）：失效名单的 TTL
     *
     * @return 已过期或解析失败返回 0
     */
    public long getRemainingExpirationMillis(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            return Math.max(expiration.getTime() - System.currentTimeMillis(), 0L);
        } catch (JwtException | IllegalArgumentException e) {
            return 0L;
        }
    }

    /** Refresh Token 有效期（毫秒），用于写入 Redis */
    public long getRefreshExpirationMillis() {
        return jwtProperties.getRefreshExpiration() * 1000L;
    }

    /** 从请求头解析令牌（形如 "Bearer xxx"） */
    public String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getTokenHeader());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String head = jwtProperties.getTokenHead();
        if (head != null && !head.isBlank() && !header.startsWith(head)) {
            return null;
        }
        return head == null || head.isBlank() ? header : header.substring(head.length());
    }

    // ==================== 内部 ====================

    private String readType(String token) {
        return readClaim(token, claims -> claims.get(CLAIM_TYPE, String.class));
    }

    private <T> T readClaim(String token, Function<Claims, T> reader) {
        try {
            return reader.apply(parseClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
