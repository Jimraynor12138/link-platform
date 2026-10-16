package com.aaf.link.core.open;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 开放接口配额扣减（P4，Redis 原子计数）。
 *
 * <p>按 access_key + 日期 计数，首次写入时设置 1 天过期；INCR 是原子的，天然支持并发。
 * 语义：先 INCR 再比较，超过配额即拒绝（多计一次被拒请求无碍，配额是粗粒度上限）。
 */
@Service
@RequiredArgsConstructor
public class ApiQuotaService {

    private static final String QUOTA_KEY_PREFIX = "link:core:open:quota:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试消耗一次配额；返回 false 表示配额已用完（调用方应拒绝并返回 429）
     */
    public boolean tryAcquire(String accessKey, Long quota) {
        if (quota == null || quota <= 0) {
            return true;
        }
        String key = QUOTA_KEY_PREFIX + accessKey + ":" + LocalDate.now();
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofDays(1));
            }
            return count != null && count <= quota;
        } catch (Exception e) {
            // fail-open：Redis 异常时不拦（配额是软限制，不能因 Redis 抖动挡住全部 API）
            return true;
        }
    }
}
