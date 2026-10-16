package com.aaf.link.auth.service.impl;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.dto.RefreshTokenDTO;
import com.aaf.link.auth.model.vo.TokenVO;
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
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.concurrent.TimeUnit;

import static com.aaf.link.auth.service.impl.AuthLoginTest.loginUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单测组 2：刷新与轮换（防重放）。
 */
@ExtendWith(MockitoExtension.class)
class AuthRefreshTokenTest {

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
    @DisplayName("刷新成功：签发新令牌并覆盖 Redis（轮换）")
    void refreshRotatesTokensWhenItIsTheLatestOne() {
        when(jwtTokenService.validateRefreshToken("rt")).thenReturn(true);
        when(jwtTokenService.getUserId("rt")).thenReturn(2L);
        when(valueOperations.get("link:auth:refresh-token:2")).thenReturn("rt");
        when(jwtTokenService.getUsername("rt")).thenReturn("tenantadmin");

        LoginUser user = loginUser(2L, 1L, "tenantadmin", true);
        when(userDetailsService.loadUserByUsername("tenantadmin")).thenReturn(user);
        when(jwtTokenService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtTokenService.generateRefreshToken(user)).thenReturn("new-refresh");
        when(jwtTokenService.getRefreshExpirationMillis()).thenReturn(604_800_000L);

        Result<TokenVO> result = authService.refreshToken(refreshDTO("rt"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("new-access");
        assertThat(result.getData().getRefreshToken()).isEqualTo("new-refresh");
        verify(valueOperations).set(eq("link:auth:refresh-token:2"), eq("new-refresh"),
                anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("旧刷新令牌被拒绝：与 Redis 中的最新值不一致（轮换防重放）")
    void refreshRejectedWhenNotLatestInRedis() {
        when(jwtTokenService.validateRefreshToken("old")).thenReturn(true);
        when(jwtTokenService.getUserId("old")).thenReturn(2L);
        when(valueOperations.get("link:auth:refresh-token:2")).thenReturn("newer-token");

        Result<TokenVO> result = authService.refreshToken(refreshDTO("old"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0401");
        verify(jwtTokenService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("类型不对（拿 Access Token 去刷新）直接拒绝，不碰 Redis")
    void refreshRejectedWhenTokenTypeInvalid() {
        when(jwtTokenService.validateRefreshToken("access-token")).thenReturn(false);

        Result<TokenVO> result = authService.refreshToken(refreshDTO("access-token"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0401");
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("刷新时重新查库：用户已不存在则拒绝")
    void refreshRejectedWhenUserNotFound() {
        when(jwtTokenService.validateRefreshToken("rt")).thenReturn(true);
        when(jwtTokenService.getUserId("rt")).thenReturn(2L);
        when(valueOperations.get("link:auth:refresh-token:2")).thenReturn("rt");
        when(jwtTokenService.getUsername("rt")).thenReturn("ghost");
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("not found"));

        Result<TokenVO> result = authService.refreshToken(refreshDTO("rt"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0401");
    }

    private static RefreshTokenDTO refreshDTO(String token) {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken(token);
        return dto;
    }
}
