package com.aaf.link.core.config;

import com.aaf.link.core.client.ClassifiedErrorDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Feign 配置（对所有 Feign 客户端生效）。
 *
 * <p><b>安全默认：不重试。</b>
 * <ul>
 *   <li>不注册 {@link feign.Retryer} Bean → Feign 使用默认的 {@code NEVER_RETRY}；</li>
 *   <li>{@link ErrorDecoder} 传 {@code retryOnServerError = false} → 5xx/4xx 都转
 *       {@code BizException}，不会触发重试。</li>
 * </ul>
 *
 * <p>只有**显式声明为幂等读**的客户端才在
 * {@code @FeignClient(configuration = IdempotentReadFeignConfig.class)} 里开启重试。
 * 这样将来新增写接口客户端时**默认就是安全的**，不会因继承全局重试而产生重复提交。
 *
 * <p>超时与日志级别走配置：{@code spring.cloud.openfeign.client.config.default.*}（见 application.yml）。
 */
@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new ClassifiedErrorDecoder(objectMapper, false);
    }
}
