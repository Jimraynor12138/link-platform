package com.aaf.link.auth.service;

import com.aaf.link.auth.model.dto.LoginDTO;
import com.aaf.link.auth.model.dto.RefreshTokenDTO;
import com.aaf.link.auth.model.vo.LoginVO;
import com.aaf.link.auth.model.vo.TokenVO;
import com.aaf.link.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证服务：登录 / 刷新（轮换） / 登出（撤销）。
 */
public interface AuthService {

    /**
     * 登录：返回双令牌，并把 Refresh Token 写入 Redis
     */
    Result<LoginVO> login(LoginDTO loginDTO);

    /**
     * 刷新：校验并轮换双令牌（旧 Refresh Token 立即失效）
     */
    Result<TokenVO> refreshToken(RefreshTokenDTO refreshTokenDTO);

    /**
     * 登出：把 Access Token 剩余有效期写入失效名单，并删除 Redis 中的 Refresh Token
     */
    Result<Void> logout(HttpServletRequest request);
}
