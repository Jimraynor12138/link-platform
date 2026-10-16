package com.aaf.link.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置（link.gateway.auth）。
 *
 * <p>JWT 密钥与 link-auth 共用，统一由环境变量 {@code LINK_AUTH_JWT_SECRET} 注入。
 * 沿用 P1 的 S1 教训：<b>不给默认值 + 启动期校验</b>，避免"忘配置就用可预测密钥"。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.gateway.auth")
public class JwtVerifyProperties {

    /** 签名密钥（HS256 要求至少 32 字节） */
    private String secret;

    private String tokenHeader = "Authorization";

    private String tokenHead = "Bearer ";

    /** 免鉴权路径（精确匹配） */
    private List<String> whitelist = new ArrayList<>(List.of("/auth/login", "/auth/refresh"));

    @PostConstruct
    void validate() {
        String value = this.secret;
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥：请设置环境变量 LINK_AUTH_JWT_SECRET（网关与 link-auth 必须用同一密钥）");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 签名密钥不足 32 字节");
        }
    }
}
