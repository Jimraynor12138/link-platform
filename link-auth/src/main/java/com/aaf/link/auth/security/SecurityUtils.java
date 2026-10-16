package com.aaf.link.auth.security;

import com.aaf.link.auth.model.dto.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具。
 *
 * <p>该类只做一件事：从 {@code SecurityContextHolder} 取当前认证主体。
 * 放在安全包里而不是过滤器里 —— 它服务于 Controller 等调用方，
 * 与"过滤请求"的职责无关（原实现把它塞在 {@code JwtAuthenticationFilter} 里，语义不清）。
 *
 * <p>注意：本类依赖 spring-security，因此放在 auth 模块；不能下沉到 link-common
 * （common 的依赖白名单里没有 security，以免污染 WebFlux 网关）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 当前登录用户；未认证或主体类型不符时返回 null
     */
    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
