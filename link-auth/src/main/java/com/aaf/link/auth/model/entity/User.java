package com.aaf.link.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（多租户）。
 *
 * tenant_id = 0 表示平台级账号（见 CommonConstants.PLATFORM_TENANT_ID），> 0 表示租户级。
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户：0=平台级 >0=租户级 */
    private Long tenantId;

    private String username;

    /** BCrypt 摘要，不存明文 */
    private String password;

    private String nickname;

    /** 状态：1 正常 0 禁用 */
    private Integer status;

    @TableLogic
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
