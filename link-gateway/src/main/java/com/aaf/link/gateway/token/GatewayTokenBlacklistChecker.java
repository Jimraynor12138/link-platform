package com.aaf.link.gateway.token;

import com.aaf.link.common.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 网关侧"登出撤销"校验（jti 黑名单）。
 *
 * <p>key 前缀必须与 link-auth 的 {@code TokenBlacklistService} 保持一致
 * （{@code link:auth:token:blacklist:{jti}}）。网关验签后查一次 Redis 判断令牌是否已被登出撤销。
 *
 * <p>Redis 异常时 fail-open（放行）：与 auth 侧语义一致——宁可短暂放过撤销，
 * 也不能因为 Redis 抖动把全站拦成 401。
 */
@Component
@RequiredArgsConstructor
public class GatewayTokenBlacklistChecker {

    private final StringRedisTemplate stringRedisTemplate;

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(CommonConstants.TOKEN_BLACKLIST_KEY_PREFIX + jti));
        } catch (Exception e) {
            return false;
        }
    }
}
