package com.aaf.link.auth.security;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.token.JwtTokenService;
import com.aaf.link.auth.token.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）。
 *
 * <p>职责只有一件：解析令牌并填充安全上下文。放行还是拦截交给安全链决定 ——
 * 白名单只在这里之外的配置里声明一次，避免"两处各写一份白名单导致不一致"。
 *
 * <p>处理顺序：类型校验 → 失效名单校验（jti）→ 查库加载用户（权限/禁用状态即时生效）→ 填充上下文。
 * 任一步失败都<b>不抛异常</b>，只是不填充上下文，最终由 AuthenticationEntryPoint 统一返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = jwtTokenService.resolveToken(request);

        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtTokenService.validateAccessToken(token)
                        && !tokenBlacklistService.isBlacklisted(jwtTokenService.getTokenId(token))) {

                    String username = jwtTokenService.getUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // 禁用用户即使持有有效令牌也不放行
                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("令牌有效但用户已禁用，按未认证处理: username={}", username);
                    }
                }
            } catch (JwtException | AuthenticationException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                log.debug("令牌解析/加载用户失败，按未认证处理: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 便于测试与复用：从已认证的上下文中取认证主体
     */
    public static LoginUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
