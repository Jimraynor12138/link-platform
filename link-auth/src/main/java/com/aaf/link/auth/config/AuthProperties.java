package com.aaf.link.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
