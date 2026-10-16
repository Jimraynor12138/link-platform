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
 *
 * <p>令牌相关配置已拆分到 {@link JwtProperties}（{@code link.auth.jwt.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.auth")
public class AuthProperties {

    /**
     * 免认证路径。
     *
     * <p><b>为什么要有代码级兜底</b>：该列表放在 Nacos 配置中心（可动态调整），
     * 但配置中心不可用时若不兜底就会变成"空白名单" —— 服务能启动，却连 {@code /auth/login}
     * 都被拦成 401，等于**把登录锁死**。
     * 因此这里给一个最小可用兜底：至少保证登录与刷新可用；Nacos 可用时其配置会整体覆盖本默认值。
     */
    private List<String> permitAll = new ArrayList<>(List.of("/auth/login", "/auth/refresh"));
}
