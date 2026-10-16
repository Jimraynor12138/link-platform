package com.aaf.link.core.config;

import com.aaf.link.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Response;
import feign.Retryer;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Feign 基础设施：重试、日志、错误解码。
 *
 * <p>超时与日志级别走配置（{@code spring.cloud.openfeign.client.config.default.*}，见 application.yml），
 * 这里只放"配置表达不了"的两项：
 * <ul>
 *   <li>{@link Retryer}：Feign 默认是 {@code NEVER_RETRY}，要重试必须给 Bean。
 *       <b>只对幂等读操作安全</b>——本阶段只有探活读接口，故开启；写接口接入前必须重新评估（重复提交风险）</li>
 *   <li>{@link ErrorDecoder}：把下游的 {@code Result} 错误码转成本服务的 {@link BizException}，
 *       避免上层拿到裸的 FeignException 而丢失业务语义</li>
 * </ul>
 *
 * <p>注意：重试与超时会叠加——总耗时上限 ≈ (connectTimeout + readTimeout) × maxAttempts，
 * 所以重试次数不能贪多，否则会放大下游的雪崩。
 */
@Slf4j
@Configuration
public class FeignConfig {

    /**
     * 重试策略：初始间隔 100ms、最大间隔 500ms、最多 2 次尝试（即最多重试 1 次）
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100L, 500L, 2);
    }

    /**
     * 错误解码：统一转 BizException，并把下游的错误码/消息带出来
     */
    @Bean
    public ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> {
            String body = readBody(response);
            String code = "A0500";
            String message = "下游服务调用失败";
            try {
                if (body != null && !body.isBlank()) {
                    JsonNode node = objectMapper.readTree(body);
                    if (node.hasNonNull("code")) {
                        code = node.get("code").asText();
                    }
                    if (node.hasNonNull("message")) {
                        message = node.get("message").asText();
                    }
                }
            } catch (Exception e) {
                log.warn("解析下游错误响应失败，使用兜底错误码: method={}, status={}", methodKey, response.status());
            }
            log.warn("下游调用失败: method={}, status={}, downstreamCode={}", methodKey, response.status(), code);
            return new BizException(code, message);
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try {
            return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
