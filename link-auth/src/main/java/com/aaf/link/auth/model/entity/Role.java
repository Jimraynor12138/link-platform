package com.aaf.link.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色：tenant_id = 0 为平台角色，> 0 为租户角色。
 */
@Data
@TableName("t_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 角色码，如 PLATFORM_ADMIN / TENANT_ADMIN / VIEWER */
    private String code;

    private String name;

    private String remark;

    @TableLogic
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
