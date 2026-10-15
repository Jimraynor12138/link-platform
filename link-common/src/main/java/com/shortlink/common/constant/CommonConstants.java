package com.shortlink.common.constant;

/**
 * 跨服务常量（P0 占位，后续版本逐步沉淀）。
 */
public final class CommonConstants {

    /** 平台级租户标识：平台超管不属于任何租户，tenant_id 固定为 0 */
    public static final long PLATFORM_TENANT_ID = 0L;

    private CommonConstants() {
    }
}
