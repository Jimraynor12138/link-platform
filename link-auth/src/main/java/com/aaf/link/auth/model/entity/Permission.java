package com.aaf.link.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限（权限码，用于接口级访问控制）。
 */
@Data
@TableName("t_permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码，如 user:manage */
    private String code;

    private String name;

    private LocalDateTime createTime;
}
