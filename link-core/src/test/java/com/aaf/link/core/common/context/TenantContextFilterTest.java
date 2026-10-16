package com.aaf.link.core.common.context;

import com.aaf.link.common.constant.GatewayAuthHeaders;
import com.aaf.link.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户上下文过滤器单测：验证"读头注入 + finally 清理"（防线程池复用串租户）。
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @Test
    void setsTenantDuringChainAndClearsAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayAuthHeaders.TENANT_ID, "5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Long> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(TenantContext.getTenantId());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo(5L);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void noHeaderLeavesContextNullAndClears() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void invalidHeaderIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayAuthHeaders.TENANT_ID, "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Long> seen = new AtomicReference<>(1L);
        FilterChain chain = (req, res) -> seen.set(TenantContext.getTenantId());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
