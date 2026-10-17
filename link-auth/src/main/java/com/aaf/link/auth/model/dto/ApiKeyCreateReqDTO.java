package com.aaf.link.auth.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成 API Key 请求（P4 创建 / P6 租户自助）。
 */
@Data
public class ApiKeyCreateReqDTO {

    /**
     * 所属租户。
     *
     * <p>平台管理员（{@code tenant:manage}）创建时<b>必填</b>（可跨租户）；
     * 租户管理员（{@code apikey:manage}）可不传——服务端自动收敛为本租户，
     * 显式传其他租户会被拒绝。
     */
    private Long tenantId;

    /** 调用配额（可选，默认 100000） */
    private Long quota;

    /** 过期时间（可选，null=永久） */
    private LocalDateTime expireAt;
}
