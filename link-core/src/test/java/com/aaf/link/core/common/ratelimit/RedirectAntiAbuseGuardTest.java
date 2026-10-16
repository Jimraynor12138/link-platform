package com.aaf.link.core.common.ratelimit;

import com.aaf.link.core.config.LinkCoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 跳转防刷守卫单测：首次记录 / 窗口内重复跳过 / 关闭放行 / Redis 异常 fail-open。
 */
@ExtendWith(MockitoExtension.class)
class RedirectAntiAbuseGuardTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LinkCoreProperties properties;
    private RedirectAntiAbuseGuard guard;

    @BeforeEach
    void setUp() {
        properties = new LinkCoreProperties();
        guard = new RedirectAntiAbuseGuard(stringRedisTemplate, properties);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void firstClickInWindowRecords() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(guard.shouldRecord("abc123", "1.2.3.4")).isTrue();
    }

    @Test
    void repeatedClickInWindowSkips() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(guard.shouldRecord("abc123", "1.2.3.4")).isFalse();
    }

    @Test
    void disabledAlwaysRecords() {
        properties.getAntiAbuse().setEnabled(false);

        assertThat(guard.shouldRecord("abc123", "1.2.3.4")).isTrue();
    }

    @Test
    void redisFailureFailsOpen() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(guard.shouldRecord("abc123", "1.2.3.4")).isTrue();
    }
}
