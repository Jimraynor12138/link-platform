package com.aaf.link.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放接口凭据信息（auth 内部接口返回给 core 的服务间契约）。
 *
 * <p>secret 用于 HMAC 签名校验，仅服务间内网传递，绝不返回给外部调用方。
 */
@Data
public class ApiKeyInfo {

    private Long id;

    private Long tenantId;

    /** 访问标识（公开） */
    private String accessKey;

    /** 密钥（签名用） */
    private String secret;

    /** 调用配额 */
    private Long quota;

    /** 过期时间，null=永久 */
    private LocalDateTime expireAt;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
