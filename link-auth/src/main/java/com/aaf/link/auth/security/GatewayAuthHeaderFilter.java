package com.aaf.link.auth.security;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.common.constant.GatewayAuthHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 读"网关透传身份头"构建认证上下文（网关模式，优先于 token 解析）。
 *
 * <p>网关已验签并把 {@code X-User-Name} 等头写入请求；本过滤器据此查库加载最新
 * 用户状态与权限，复用 {@code loadUserByUsername} —— 因此<b>禁用即时生效、权限即时生效</b>的
 * 特性不变（与"每请求查库"的既有取舍一致，见 M3）。
 *
 * <p><b>信任边界</b>：本过滤器信任 {@code X-User-Name} 头。该信任成立的前提是
 * "请求必须来自网关"（网关已剥离伪造头并写入真实身份）。本地开发若直连 8081 并伪造该头，
 * 仍可越权——真正的隔离靠 P7 网络层（只暴露网关端口）。
 *
 * <p>保留原 token 解析链路（{@link JwtAuthenticationFilter}）：当无身份头时（直连/调试），
 * 仍走原有 token 解析，兼容 P3 之前的调用方式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthHeaderFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String username = request.getHeader(GatewayAuthHeaders.USER_NAME);

        if (!StringUtils.hasText(username)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);
            if (loginUser.isEnabled()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("网关身份头对应用户已禁用，不放行: username={}", username);
            }
        } catch (UsernameNotFoundException e) {
            log.warn("网关身份头对应用户不存在: username={}", username);
        }

        filterChain.doFilter(request, response);
    }
}
