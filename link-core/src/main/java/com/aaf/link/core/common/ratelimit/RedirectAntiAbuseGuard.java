package com.aaf.link.core.common.ratelimit;

import com.aaf.link.core.config.LinkCoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 跳转链路防刷（V4）：单 IP 对同一短码高频点击的"统计去重"，防统计污染。
 *
 * <p><b>为什么是"去重"而非"限流"</b>：跳转是核心读服务，绝不能被"限流拒绝"——访客点了就必须跳；
 * 这里只在统计上报前去重（同一 IP 对同一短码窗口内只记一次），既防刷 PV/UV，又不影响跳转本身。
 *
 * <p><b>为什么用 Redis SETNX 而非进程内</b>：防刷要跨实例生效（多实例下进程内去重会各自放行），
 * SETNX 是原子的且 O(1)。<b>fail-open</b>：Redis 异常时放行统计（防刷是软能力，不能拖慢跳转）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectAntiAbuseGuard {

    private static final String KEY_PREFIX = "link:core:anti-abuse:";

    private final StringRedisTemplate stringRedisTemplate;
    private final LinkCoreProperties properties;

    /**
     * 本次点击是否应记录统计（false 表示窗口内已记录过，应跳过统计上报）
     */
    public boolean shouldRecord(String code, String clientIp) {
        LinkCoreProperties.AntiAbuse config = properties.getAntiAbuse();
        if (!config.isEnabled()) {
            return true;
        }
        String key = KEY_PREFIX + code + ":" + clientIp;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(config.getWindowSeconds())));
        } catch (Exception e) {
            log.warn("跳转防刷 Redis 异常，放行统计（fail-open）: code={}", code, e);
            return true;
        }
    }
}
