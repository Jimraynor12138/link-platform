package com.aaf.link.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户（SaaS 客户）。
 */
@Data
@TableName("t_tenant")
public class Tenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 状态：1 正常 0 停用 */
    private Integer status;

    /** 每日创建短链配额 */
    private Long quotaPerDay;

    @TableLogic
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
