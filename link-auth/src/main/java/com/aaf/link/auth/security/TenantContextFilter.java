package com.aaf.link.auth.security;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器（在 {@link JwtAuthenticationFilter} 之后执行）。
 *
 * <p>把令牌解析出的 tenantId 注入 {@link TenantContext}（ThreadLocal），供下游业务代码读取。
 * <b>租户标识只来自令牌，永不接受前端传入</b> —— 这是防租户越权的第一道闸。
 *
 * <p><b>顺序依赖（重要）</b>：本过滤器依赖 {@link JwtAuthenticationFilter} 先填充安全上下文
 * （见 {@code SecurityConfig#securityFilterChain} 中的 {@code addFilterAfter}）。
 * 若顺序被调整，本过滤器会取不到主体 —— 为把这种"静默失效"变成可见问题，
 * 当"已有认证主体、却没有 tenantId"时打 WARN 日志。
 *
 * <p>注意：必须在 finally 中清理。Web 容器线程池会复用线程，
 * 不清理会把上一个请求的租户身份带给下一个请求（最隐蔽的越权 bug）。
 */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
                if (loginUser.getTenantId() == null) {
                    // 静默失效告警：有认证主体却没有租户标识，通常意味着过滤器顺序被改过，或多租户上下文传递断链
                    log.warn("已认证但租户标识为空，多租户隔离将失效，请检查过滤器顺序与登录流程: username={}",
                            loginUser.getUsername());
                } else {
                    TenantContext.setTenantId(loginUser.getTenantId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用会串租户，必须清理
            TenantContext.clear();
        }
    }
}
