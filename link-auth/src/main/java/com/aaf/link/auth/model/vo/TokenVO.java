package com.aaf.link.auth.model.vo;

import lombok.Data;

/**
 * 刷新令牌响应（轮换后的新令牌对）。
 */
@Data
public class TokenVO {

    private String accessToken;

    private String refreshToken;
}
