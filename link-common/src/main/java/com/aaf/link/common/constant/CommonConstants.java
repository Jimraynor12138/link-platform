package com.aaf.link.common.constant;

/**
 * 跨服务常量（P0 占位，后续版本逐步沉淀）。
 */
public final class CommonConstants {

    /** 平台级租户标识：平台超管不属于任何租户，tenant_id 固定为 0 */
    public static final long PLATFORM_TENANT_ID = 0L;

    /** 令牌失效名单（黑名单）Redis key 前缀：{jti} 为令牌唯一标识。网关与 auth 共用，避免两处硬编码漂移 */
    public static final String TOKEN_BLACKLIST_KEY_PREFIX = "link:auth:token:blacklist:";

    private CommonConstants() {
    }
}
