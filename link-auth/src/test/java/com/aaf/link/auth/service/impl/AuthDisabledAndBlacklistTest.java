package com.aaf.link.auth.service.impl;

import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.dto.RefreshTokenDTO;
import com.aaf.link.auth.model.vo.TokenVO;
import com.aaf.link.auth.security.JwtAuthenticationFilter;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * 单测组 3：禁用拦截与令牌撤销（失效名单）。
 */
@ExtendWith(MockitoExtension.class)
class AuthDisabledAndBlacklistTest {

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
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("禁用用户无法续期：刷新时重新查库发现已禁用")
    void disabledUserCannotRefresh() {
        when(jwtTokenService.validateRefreshToken("rt")).thenReturn(true);
        when(jwtTokenService.getUserId("rt")).thenReturn(4L);
        when(valueOperations.get("link:auth:refresh-token:4")).thenReturn("rt");
        when(jwtTokenService.getUsername("rt")).thenReturn("disabled");

        LoginUser disabledUser = loginUser(4L, 1L, "disabled", false);
        when(userDetailsService.loadUserByUsername("disabled")).thenReturn(disabledUser);

        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("rt");
        Result<TokenVO> result = authService.refreshToken(dto);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("A0403");
        verify(jwtTokenService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("登出：Access Token 进失效名单（TTL=剩余有效期），并删除 Redis 中的 Refresh Token")
    void logoutBlacklistsAccessTokenAndDeletesRefreshToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenService.resolveToken(request)).thenReturn("access-token");
        when(jwtTokenService.getTokenId("access-token")).thenReturn("jti-9");
        when(jwtTokenService.getRemainingExpirationMillis("access-token")).thenReturn(60_000L);
        when(jwtTokenService.getUserId("access-token")).thenReturn(3L);

        Result<Void> result = authService.logout(request);

        assertThat(result.isSuccess()).isTrue();
        verify(tokenBlacklistService).blacklist("jti-9", 60_000L);
        verify(stringRedisTemplate).delete("link:auth:refresh-token:3");
    }

    @Test
    @DisplayName("登出无令牌也视为成功（幂等）")
    void logoutWithoutTokenIsIdempotent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Result<Void> result = authService.logout(request);

        assertThat(result.isSuccess()).isTrue();
        verify(tokenBlacklistService, never()).blacklist(any(), anyLong());
    }

    @Test
    @DisplayName("失效名单：以 jti 为 key 写入，TTL 为剩余有效期")
    void blacklistWritesJtiKeyWithTtl() {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);

        service.blacklist("jti-1", 30_000L);

        verify(valueOperations).set(eq("link:auth:token:blacklist:jti-1"), eq("1"),
                eq(30_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("失效名单：已过期令牌（剩余 0）不写入，避免无意义 key")
    void blacklistSkipsAlreadyExpiredToken() {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);

        service.blacklist("jti-1", 0L);

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("失效名单查询：命中返回 true")
    void isBlacklistedReadsRedis() {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);
        when(stringRedisTemplate.hasKey("link:auth:token:blacklist:jti-1")).thenReturn(true);
        when(stringRedisTemplate.hasKey("link:auth:token:blacklist:jti-2")).thenReturn(false);

        assertThat(service.isBlacklisted("jti-1")).isTrue();
        assertThat(service.isBlacklisted("jti-2")).isFalse();
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    @DisplayName("过滤器：令牌在失效名单中时，不填充安全上下文、也不查库")
    void blacklistedTokenIsNotAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenService, tokenBlacklistService, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked");

        when(jwtTokenService.resolveToken(request)).thenReturn("revoked");
        when(jwtTokenService.validateAccessToken("revoked")).thenReturn(true);
        when(jwtTokenService.getTokenId("revoked")).thenReturn("jti-revoked");
        when(tokenBlacklistService.isBlacklisted("jti-revoked")).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("过滤器：有效令牌会填充安全上下文")
    void validTokenIsAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenService, tokenBlacklistService, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good");

        LoginUser user = loginUser(3L, 1L, "viewer", true);
        when(jwtTokenService.resolveToken(request)).thenReturn("good");
        when(jwtTokenService.validateAccessToken("good")).thenReturn(true);
        when(jwtTokenService.getTokenId("good")).thenReturn("jti-good");
        when(tokenBlacklistService.isBlacklisted("jti-good")).thenReturn(false);
        when(jwtTokenService.getUsername("good")).thenReturn("viewer");
        when(userDetailsService.loadUserByUsername("viewer")).thenReturn(user);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(LoginUser.class);
        SecurityContextHolder.clearContext();
    }
}
