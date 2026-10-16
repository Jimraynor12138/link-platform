package com.aaf.link.core.client;

import com.aaf.link.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 按 HTTP 状态分类的 Feign 错误解码器。
 *
 * <p><b>错误分类决定重试语义</b>：
 * <ul>
 *   <li>4xx（业务/参数错误）→ {@link BizException}，重试无意义；</li>
 *   <li>5xx（下游瞬态故障）→ 由 {@code retryOnServerError} 决定：
 *       {@code true} 抛 {@link RetryableException} 触发重试（<b>仅幂等读可用</b>）；
 *       {@code false} 抛 {@link BizException} 直接失败（<b>写操作必须如此</b>，否则会重复提交）。</li>
 * </ul>
 *
 * <p>因此全局默认用 {@code false}，只有显式声明幂等的客户端才开启重试 —— <b>安全默认、显式开启</b>。
 */
@Slf4j
public class ClassifiedErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;
    private final boolean retryOnServerError;

    public ClassifiedErrorDecoder(ObjectMapper objectMapper, boolean retryOnServerError) {
        this.objectMapper = objectMapper;
        this.retryOnServerError = retryOnServerError;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String[] parsed = parseDownstreamError(response);
        String code = parsed[0];
        String message = parsed[1];

        if (status >= 500) {
            if (retryOnServerError) {
                log.warn("下游 5xx，转 RetryableException 触发重试: method={}, status={}, code={}",
                        methodKey, status, code);
                Request.HttpMethod httpMethod = response.request() != null
                        ? response.request().httpMethod() : Request.HttpMethod.GET;
                return new RetryableException(status, message, httpMethod, (Long) null, response.request());
            }
            log.warn("下游 5xx，按失败处理（不重试）: method={}, status={}, code={}", methodKey, status, code);
            return new BizException(code, message);
        }

        log.warn("下游 4xx，按业务错误处理（不重试）: method={}, status={}, code={}", methodKey, status, code);
        return new BizException(code, message);
    }

    private String[] parseDownstreamError(Response response) {
        String code = "A0500";
        String message = "下游服务调用失败";
        try {
            if (response.body() != null) {
                String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                if (body != null && !body.isBlank()) {
                    JsonNode node = objectMapper.readTree(body);
                    if (node.hasNonNull("code")) {
                        code = node.get("code").asText();
                    }
                    if (node.hasNonNull("message")) {
                        message = node.get("message").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析下游错误响应失败，使用兜底错误码: status={}", response.status());
        }
        return new String[]{code, message};
    }
}
