package com.aaf.link.auth.service.impl;

import com.aaf.link.auth.model.dto.LoginDTO;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.dto.RefreshTokenDTO;
import com.aaf.link.auth.model.vo.LoginVO;
import com.aaf.link.auth.model.vo.TokenVO;
import com.aaf.link.auth.service.AuthService;
import com.aaf.link.auth.token.JwtTokenService;
import com.aaf.link.auth.token.TokenBlacklistService;
import com.aaf.link.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 *
 * <p>三条链路的要点：
 * <ul>
 *   <li><b>登录</b>：交给 AuthenticationManager 做密码校验（BCrypt），成功后签发双令牌，
 *       Refresh Token 存 Redis（key 带 userId），TTL = Refresh 有效期</li>
 *   <li><b>刷新</b>：先校验类型，再与 Redis 中的当前值<b>比对</b> —— 不是最新的一律拒绝（轮换防重放）；
 *       然后<b>重新查库加载用户与权限</b>，禁用用户无法续期；成功后覆盖 Redis 中的 Refresh Token</li>
 *   <li><b>登出</b>：把 Access Token 的 jti 写入失效名单（TTL = 剩余有效期），删除 Redis 中的 Refresh Token，
 *       清空安全上下文</li>
 * </ul>
 *
 * <p>日志只记事件与用户标识，绝不打印令牌明文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "link:auth:refresh-token:";

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<LoginVO> login(LoginDTO loginDTO) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword()));
        } catch (DisabledException e) {
            log.warn("登录被拒（用户已禁用）: username={}", loginDTO.getUsername());
            return Result.failure("A0403", "用户已禁用");
        } catch (AuthenticationException e) {
            log.warn("登录失败（凭据错误）: username={}", loginDTO.getUsername());
            return Result.failure("A0401", "用户名或密码错误");
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String accessToken = jwtTokenService.generateAccessToken(loginUser);
        String refreshToken = jwtTokenService.generateRefreshToken(loginUser);
        storeRefreshToken(loginUser.getId(), refreshToken);

        log.info("登录成功: userId={}, tenantId={}, username={}",
                loginUser.getId(), loginUser.getTenantId(), loginUser.getUsername());

        LoginVO vo = new LoginVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        vo.setTenantId(loginUser.getTenantId());
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        return Result.success(vo);
    }

    @Override
    public Result<TokenVO> refreshToken(RefreshTokenDTO refreshTokenDTO) {
        String refreshToken = refreshTokenDTO.getRefreshToken();

        if (!jwtTokenService.validateRefreshToken(refreshToken)) {
            return Result.failure("A0401", "刷新令牌无效或已过期");
        }

        Long userId = jwtTokenService.getUserId(refreshToken);
        if (userId == null) {
            return Result.failure("A0401", "刷新令牌无效");
        }

        // 轮换防重放：只认 Redis 中的最新值，旧刷新令牌用过一次即失效
        String currentRefreshToken = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
        if (!refreshToken.equals(currentRefreshToken)) {
            log.warn("刷新被拒（令牌已失效或非最新）: userId={}", userId);
            return Result.failure("A0401", "刷新令牌已失效，请重新登录");
        }

        String username = jwtTokenService.getUsername(refreshToken);
        if (!StringUtils.hasText(username)) {
            return Result.failure("A0401", "刷新令牌无效");
        }

        // 重新加载用户与权限：禁用用户无法续期，权限变更即时生效
        LoginUser loginUser;
        try {
            loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return Result.failure("A0401", "用户不存在");
        }
        if (!loginUser.isEnabled()) {
            log.warn("刷新被拒（用户已禁用）: userId={}", userId);
            return Result.failure("A0403", "用户已禁用，无法刷新令牌");
        }

        String newAccessToken = jwtTokenService.generateAccessToken(loginUser);
        String newRefreshToken = jwtTokenService.generateRefreshToken(loginUser);
        storeRefreshToken(userId, newRefreshToken);

        log.info("令牌刷新成功: userId={}, tenantId={}", userId, loginUser.getTenantId());

        TokenVO tokenVO = new TokenVO();
        tokenVO.setAccessToken(newAccessToken);
        tokenVO.setRefreshToken(newRefreshToken);
        return Result.success(tokenVO);
    }

    @Override
    public Result<Void> logout(HttpServletRequest request) {
        String token = jwtTokenService.resolveToken(request);

        // 没有令牌也视为退出成功（幂等）
        if (!StringUtils.hasText(token)) {
            return Result.success();
        }

        String tokenId = jwtTokenService.getTokenId(token);
        long remaining = jwtTokenService.getRemainingExpirationMillis(token);
        if (tokenId != null && remaining > 0) {
            tokenBlacklistService.blacklist(tokenId, remaining);
        }

        Long userId = jwtTokenService.getUserId(token);
        if (userId != null) {
            stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        }

        SecurityContextHolder.clearContext();
        log.info("登出成功: userId={}", userId);
        return Result.success();
    }

    private void storeRefreshToken(Long userId, String refreshToken) {
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                jwtTokenService.getRefreshExpirationMillis(),
                TimeUnit.MILLISECONDS);
    }
}
