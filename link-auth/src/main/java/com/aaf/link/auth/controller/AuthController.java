package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.LoginDTO;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.dto.RefreshTokenDTO;
import com.aaf.link.auth.model.vo.LoginVO;
import com.aaf.link.auth.model.vo.TokenVO;
import com.aaf.link.auth.model.vo.UserInfoVO;
import com.aaf.link.auth.security.JwtAuthenticationFilter;
import com.aaf.link.auth.service.AuthService;
import com.aaf.link.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录（白名单，无需令牌）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return authService.login(loginDTO);
    }

    /**
     * 刷新令牌（需携带 Refresh Token 本体，无需 Access Token）
     */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshTokenDTO refreshTokenDTO) {
        return authService.refreshToken(refreshTokenDTO);
    }

    /**
     * 登出：撤销当前 Access Token 并删除 Refresh Token
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        return authService.logout(request);
    }

    /**
     * 当前登录用户信息（受保护接口，用于验证令牌访问与租户上下文）
     */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        LoginUser loginUser = JwtAuthenticationFilter.currentUser();
        UserInfoVO vo = new UserInfoVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        vo.setNickname(loginUser.getNickname());
        vo.setTenantId(loginUser.getTenantId());
        vo.setRoles(loginUser.getRoles());
        vo.setPermissions(loginUser.getPermissions());
        return Result.success(vo);
    }

    /**
     * 演示接口级鉴权：需要 user:manage 权限（只读账号访问会返回 403）
     */
    @GetMapping("/manage-check")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<String> manageCheck() {
        return Result.success("ok");
    }
}
