package com.aaf.link.auth.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 列表项（P6 平台侧管理用）。
 *
 * <p><b>绝不包含 secret</b>：secret 只在生成时一次性返回（{@link ApiKeyCreateVO}），
 * 之后任何人都无法再查看。
 */
@Data
public class ApiKeyVO {

    private String accessKey;

    private Long quota;

    /** 过期时间，null 表示永久 */
    private LocalDateTime expireAt;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createTime;
}
