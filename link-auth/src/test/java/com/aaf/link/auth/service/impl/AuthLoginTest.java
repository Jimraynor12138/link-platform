package com.aaf.link.auth.service.impl;

import com.aaf.link.auth.model.dto.LoginDTO;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.vo.LoginVO;
import com.aaf.link.auth.token.JwtTokenService;
import com.aaf.link.auth.token.TokenBlacklistService;
import com.aaf.link.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单测组 1：登录。
 */
@ExtendWith(MockitoExtension.class)
class AuthLoginTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        authService = new AuthServiceImpl(authenticationManager, userDetailsService,
                jwtTokenService, tokenBlacklistService, stringRedisTemplate);
    }

    @Test
    @DisplayName("登录成功：返回双令牌，并把 Refresh Token 按用户维度写入 Redis")
    void loginSuccessReturnsTokensAndStoresRefreshToken() {
        LoginUser principal = loginUser(1L, 0L, "superadmin", true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenService.generateAccessToken(principal)).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(principal)).thenReturn("refresh-token");
        when(jwtTokenService.getRefreshExpirationMillis()).thenReturn(604_800_000L);

        Result<LoginVO> result = authService.login(loginDTO("superadmin", "123456"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(result.getData().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getData().getTenantId()).isZero();
        verify(valueOperations).set(eq("link:auth:refresh-token:1"), eq("refresh-token"),
                eq(604_800_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("密码错误：返回认证失败，不签发令牌")
    void loginWithBadCredentialsFails() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("用户名或密码错误"));

        Result<LoginVO> result = authService.login(loginDTO("superadmin", "wrong"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0401");
        verify(jwtTokenService, org.mockito.Mockito.never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("账号被禁用：返回禁用提示（与认证失败区分开）")
    void loginWithDisabledUserFailsWithDistinctCode() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("用户已禁用"));

        Result<LoginVO> result = authService.login(loginDTO("disabled", "123456"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0403");
    }

    static LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    static LoginUser loginUser(Long id, Long tenantId, String username, boolean enabled) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword("hashed");
        user.setNickname(username);
        user.setEnabled(enabled);
        user.setRoles(List.of("VIEWER"));
        user.setPermissions(List.of("link:read"));
        return user;
    }
}
