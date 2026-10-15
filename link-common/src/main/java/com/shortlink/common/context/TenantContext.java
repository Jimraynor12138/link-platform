package com.shortlink.common.context;

/**
 * 租户上下文（P0 占位，P1 接入 auth 认证、P4 接入 core 多租户隔离时使用）。
 *
 * 用 ThreadLocal 保存当前请求的 tenant_id，由服务端从 Token 解析后强制注入，
 * 供 MyBatis-Plus 多租户插件 / 业务代码读取，防止租户越权（不能只信前端传的 tenant_id）。
 *
 * 注意：线程池复用场景下，请求收尾必须调用 {@link #clear()}，否则会串租户。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
