package com.aaf.link.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * "幂等读"客户端的 Feign 配置（<b>只对引用它的客户端生效</b>）。
 *
 * <p>用法：{@code @FeignClient(name = "...", configuration = IdempotentReadFeignConfig.class)}
 *
 * <p><b>为什么这个类刻意不加 {@code @Configuration}</b>：
 * 它必须只对引用了它的 Feign 客户端生效。若加上 {@code @Configuration}，会被
 * {@code scanBasePackages} 扫成全局 Bean，于是"所有客户端都开启重试"——
 * 将来某个写接口被重试就会产生**重复提交**。Feign 会把 {@code configuration} 指定的类
 * 注册到该客户端的子容器中，普通类（含 {@code @Bean} 方法）即可满足。
 *
 * <p><b>只在幂等读上开启重试</b>：重试会与超时叠加（总耗时 ≈ (connect + read) × maxAttempts），
 * 次数不能贪多，否则会放大下游的雪崩。写操作**绝不能**放在这里。
 */
public class IdempotentReadFeignConfig {

    /**
     * 重试策略：初始间隔 100ms、最大间隔 500ms、最多 2 次尝试（即最多重试 1 次）
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100L, 500L, 2);
    }

    /**
     * 5xx 抛 RetryableException（触发重试）；4xx 仍按业务错误处理
     */
    @Bean
    public ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new ClassifiedErrorDecoder(objectMapper, true);
    }
}
