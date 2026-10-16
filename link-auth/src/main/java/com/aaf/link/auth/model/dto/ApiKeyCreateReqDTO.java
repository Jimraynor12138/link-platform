package com.aaf.link.auth.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成 API Key 请求（P4）。
 */
@Data
public class ApiKeyCreateReqDTO {

    /** 所属租户 */
    @NotNull(message = "租户不能为空")
    private Long tenantId;

    /** 调用配额（可选，默认 100000） */
    private Long quota;

    /** 过期时间（可选，null=永久） */
    private LocalDateTime expireAt;
}
