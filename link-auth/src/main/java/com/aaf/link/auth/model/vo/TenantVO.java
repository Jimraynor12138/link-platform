package com.aaf.link.auth.model.vo;

import lombok.Data;

/**
 * 租户列表项（P6 平台侧管理用）。
 *
 * <p>只暴露前端需要的字段，不返回 delFlag / updateTime 等内部字段。
 */
@Data
public class TenantVO {

    private Long id;

    private String name;

    /** 1 正常 / 0 停用 */
    private Integer status;

    /** 每日调用配额 */
    private Long quotaPerDay;
}
