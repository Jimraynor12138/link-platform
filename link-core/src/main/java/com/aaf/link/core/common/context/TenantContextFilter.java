package com.aaf.link.core.common.context;

import com.aaf.link.common.constant.GatewayAuthHeaders;
import com.aaf.link.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从网关透传的 {@code X-Tenant-Id} 头注入租户上下文（兑现台账 P3-3）。
 *
 * <p>网关只对<b>已认证</b>请求写入 {@code X-Tenant-Id}；访客跳转（{@code /{code}}）与
 * 未认证请求没有该头，因此 {@link TenantContext} 为空 —— 多租户拦截器据此"不过滤"，
 * 跳转/定时任务天然豁免。
 *
 * <p><b>信任前提</b>：core 信任该头（网关已剥离伪造头并写入真实值）；直连 8082 可伪造，
 * 真正的隔离靠 P7 网络层（只暴露网关端口）。
 *
 * <p>每次请求 finally 清理，避免线程池复用串租户（与 auth 的 TenantContextFilter 同款语义）。
 */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Long tenantId = parseTenantId(request.getHeader(GatewayAuthHeaders.TENANT_ID));
        TenantContext.setTenantId(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long parseTenantId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            log.warn("非法的 X-Tenant-Id 头，忽略: {}", header);
            return null;
        }
    }
}
