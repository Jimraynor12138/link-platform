package com.aaf.link.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证服务配置（link.auth.*）。
 *
 * <p>白名单在此集中声明：安全链与过滤器都从这里取，避免"两处各写一份导致不一致"
 * （参考实现中 permitAll 与过滤器的 shouldNotFilter 各存一份白名单，是越权/误拦的隐患）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();

    /** 免认证路径 */
    private List<String> permitAll = new ArrayList<>();

    /**
     * 启动期校验 JWT 密钥（fail-fast）。
     *
     * <p><b>为什么必须显式校验</b>：{@code application.yml} 里密钥写成 {@code ${LINK_AUTH_JWT_SECRET}} 且不给默认值，
     * 但 Spring Boot 绑定配置时对"无法解析的占位符"默认是<b>原样保留</b>（不报错），
     * 于是密钥会变成字面量 {@code "${LINK_AUTH_JWT_SECRET}"} —— 一个<b>可预测的密钥</b>，比"用默认值"更危险。
     * 因此这里在启动期主动拦截：既拦"未配置"，也拦"占位符没解析"，还拦"强度不足"。
     */
    @PostConstruct
    void validateJwtSecret() {
        String secret = jwt.getSecret();
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥：请通过环境变量 LINK_AUTH_JWT_SECRET 注入（本地开发写入 application-dev.yml）。"
                            + "缺失时拒绝启动，避免用可预测的密钥签发令牌");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 签名密钥不足 32 字节，HS256 不可用");
        }
    }

    /**
     * 令牌配置。
     */
    @Data
    public static class Jwt {

        /** 签名密钥（HS256 要求至少 32 字节） */
        private String secret;

        /** Access Token 有效期（秒） */
        private long expiration = 1800L;

        /** Refresh Token 有效期（秒） */
        private long refreshExpiration = 604800L;

        /** 令牌请求头名 */
        private String tokenHeader = "Authorization";

        /** 令牌前缀 */
        private String tokenHead = "Bearer ";
    }
}
