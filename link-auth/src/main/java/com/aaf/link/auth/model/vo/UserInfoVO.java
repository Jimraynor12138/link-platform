package com.aaf.link.auth.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（回显用；不含密码等敏感字段）。
 */
@Data
public class UserInfoVO {

    private Long id;

    private String username;

    private String nickname;

    /** 所属租户：0=平台级 >0=租户级 */
    private Long tenantId;

    private List<String> roles;

    private List<String> permissions;
}
