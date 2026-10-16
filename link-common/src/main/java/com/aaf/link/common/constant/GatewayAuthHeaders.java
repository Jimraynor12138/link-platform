package com.aaf.link.common.constant;

/**
 * 网关鉴权后透传给下游的身份请求头常量。
 *
 * <p>网关验签通过后写入这些头，下游据此构建认证上下文（不再各自解析 token）。
 * <b>安全前提</b>：网关必须在写入前剥离客户端传入的同名头，否则任何人都可伪造身份；
 * 且下游只能被网关访问（内网隔离），否则直连下游 + 伪造头即可越权。
 */
public final class GatewayAuthHeaders {

    private GatewayAuthHeaders() {
    }

    /** 用户 ID */
    public static final String USER_ID = "X-User-Id";

    /** 用户名（下游用它查库加载最新权限与状态） */
    public static final String USER_NAME = "X-User-Name";

    /** 租户 ID */
    public static final String TENANT_ID = "X-Tenant-Id";
}
