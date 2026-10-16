package com.aaf.link.auth.model.vo;

import lombok.Data;

/**
 * 登录响应。
 */
@Data
public class LoginVO {

    private Long id;

    private String username;

    private Long tenantId;

    private String accessToken;

    private String refreshToken;
}
