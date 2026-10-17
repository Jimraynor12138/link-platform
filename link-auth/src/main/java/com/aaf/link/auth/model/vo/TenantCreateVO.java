package com.aaf.link.auth.model.vo;

import lombok.Data;

/**
 * 新建租户结果：只回必要信息，<b>不回密码</b>。
 */
@Data
public class TenantCreateVO {

    private Long tenantId;

    private String tenantName;

    private String adminUsername;
}
