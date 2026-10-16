package com.aaf.link.common.dto;

import lombok.Data;

/**
 * 租户信息（auth 内部接口返回给 core 的服务间契约）。
 */
@Data
public class TenantInfo {

    private Long id;

    private String name;

    /** 状态：1 正常 0 停用 */
    private Integer status;

    /** 每日创建短链配额 */
    private Long quotaPerDay;
}
