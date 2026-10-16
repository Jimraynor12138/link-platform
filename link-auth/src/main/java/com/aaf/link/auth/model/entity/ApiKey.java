package com.aaf.link.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放接口凭据（P1 仅建表；签名校验与配额限流在 P4 实现）。
 */
@Data
@TableName("t_api_key")
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 访问标识（公开） */
    private String accessKey;

    /** 密钥（签名用，不对外暴露） */
    private String secret;

    /** 调用配额 */
    private Long quota;

    /** 过期时间，null 表示永久 */
    private LocalDateTime expireAt;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    @TableLogic
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
