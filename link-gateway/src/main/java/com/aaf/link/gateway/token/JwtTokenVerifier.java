package com.aaf.link.gateway.token;

import com.aaf.link.gateway.config.JwtVerifyProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 验签（只验签 + 读声明，不查库）。
 *
 * <p>与 link-auth 的 JwtTokenService 用同一密钥与声明结构；
 * 解析失败一律返回 null，由调用方按"未认证"处理。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenVerifier {

    private static final String TYPE_ACCESS = "access";
    private static final String CLAIM_TYPE = "type";

    private final JwtVerifyProperties properties;

    /** 从请求头解析令牌（"Bearer xxx"） */
    public String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(properties.getTokenHeader());
        if (header == null || header.isBlank()) {
            return null;
        }
        String head = properties.getTokenHead();
        if (head != null && !head.isBlank() && !header.startsWith(head)) {
            return null;
        }
        return head == null || head.isBlank() ? header : header.substring(head.length());
    }

    /**
     * 校验 Access Token（签名 + 类型），返回声明；失败/类型不对返回 null
     */
    public Claims verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)) ? claims : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
