package com.aaf.link.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链接实体，对应表 t_link（跳转链路唯一依赖表）
 *
 * <p>id 即发号器产出的号（IdType.INPUT），code = Base62(id)，可互相推导。
 */
@Data
@TableName("t_link")
public class LinkDO {

    /** 全局唯一 ID，由发号器产生后显式写入 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 所属租户：0=平台级 >0=租户级（多租户拦截器在 INSERT 时自动填充） */
    private Long tenantId;

    /** 短码（Base62 编码） */
    private String code;

    /** 短链域名 */
    private String domain;

    /** 原始长 URL */
    private String originalUrl;

    /** 分组标识 */
    private String gid;

    /** 渠道标识（可选，空=无渠道，用于点击归因） */
    private String channel;

    /** 启用状态：0 启用，1 停用 */
    private Integer enableStatus;

    /** 有效期类型：1 永久，2 自定义 */
    private Integer validType;

    /** 过期时间（valid_type=2 时必填） */
    private LocalDateTime validDate;

    /** 描述 */
    private String description;

    /** 逻辑删除：0 未删，1 已删 */
    @TableLogic
    private Integer delFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
