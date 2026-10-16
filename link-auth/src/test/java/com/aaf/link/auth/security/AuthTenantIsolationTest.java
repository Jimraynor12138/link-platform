package com.aaf.link.auth.security;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.common.context.TenantContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单测组 4：租户隔离（上下文注入与清理）。
 *
 * <p>核心断言：租户标识只来自令牌主体；请求结束后必须清理，否则线程复用会串租户。
 */
class AuthTenantIsolationTest {

    private final TenantContextFilter tenantContextFilter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("从令牌主体注入租户标识，并在请求结束后清理")
    void injectsTenantIdAndClearsAfterwards() throws Exception {
        authenticateAs(8L);

        AtomicReference<Long> tenantSeenInChain = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        tenantContextFilter.doFilter(request, response, (req, res) -> tenantSeenInChain.set(TenantContext.getTenantId()));

        assertThat(tenantSeenInChain.get()).isEqualTo(8L);
        // 关键：请求结束后必须为 null，否则线程池复用会串租户
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("下游抛异常时同样清理上下文（finally 保证）")
    void clearsContextWhenDownstreamThrows() {
        authenticateAs(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> tenantContextFilter.doFilter(request, response,
                (req, res) -> {
                    throw new ServletException("boom");
                })).isInstanceOf(ServletException.class);

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("未认证请求不注入任何租户标识")
    void noPrincipalMeansNoTenant() throws Exception {
        AtomicReference<Long> tenantSeenInChain = new AtomicReference<>();

        tenantContextFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> tenantSeenInChain.set(TenantContext.getTenantId()));

        assertThat(tenantSeenInChain.get()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    private void authenticateAs(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(100L);
        user.setTenantId(tenantId);
        user.setUsername("u" + tenantId);
        user.setEnabled(true);
        user.setRoles(List.of("VIEWER"));
        user.setPermissions(List.of("link:read"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
