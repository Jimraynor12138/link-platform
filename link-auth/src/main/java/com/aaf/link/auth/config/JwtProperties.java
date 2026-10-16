package com.aaf.link.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 令牌配置（link.auth.jwt.*）。
 *
 * <p>拆自原 {@link AuthProperties}：令牌参数与"免认证白名单"是两类关注点，分开后职责更清晰，
 * 绑定路径不变（yml 仍是 {@code link.auth.jwt.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.auth.jwt")
public class JwtProperties {

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
        String value = this.secret;
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥：请通过环境变量 LINK_AUTH_JWT_SECRET 注入（本地开发写入 application-dev.yml）。"
                            + "缺失时拒绝启动，避免用可预测的密钥签发令牌");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 签名密钥不足 32 字节，HS256 不可用");
        }
    }
}
