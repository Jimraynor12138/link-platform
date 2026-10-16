package com.aaf.link.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 令牌失效名单（解决 JWT 无法撤销的问题）。
 *
 * <p>登出时把令牌的 {@code jti} 写入 Redis，TTL 取令牌剩余有效期 —— 到期自动清理，无需定时任务。
 * key 用 jti（短）而不是整个令牌（200+ 字节），显著节省 Redis 内存。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "link:auth:token:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 拉黑一个令牌
     *
     * @param tokenId     令牌唯一标识（jti）
     * @param ttlMillis   剩余有效期（毫秒）；<= 0 表示已过期，无需拉黑
     */
    public void blacklist(String tokenId, long ttlMillis) {
        if (tokenId == null || tokenId.isBlank() || ttlMillis <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 令牌是否已被拉黑
     */
    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + tokenId));
    }
}
