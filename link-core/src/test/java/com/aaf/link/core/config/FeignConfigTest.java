package com.aaf.link.core.config;

import com.aaf.link.common.exception.BizException;
import com.aaf.link.core.client.IdempotentReadFeignConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 错误解码单测：验证"安全默认 + 显式开启"的重试语义。
 *
 * <ul>
 *   <li>全局解码器（默认）：5xx **不重试** → BizException</li>
 *   <li>幂等读解码器：5xx **重试** → RetryableException</li>
 *   <li>两者对 4xx 都按业务错误处理（不重试）</li>
 * </ul>
 */
class FeignConfigTest {

    private static final String METHOD_KEY = "AuthClient#ping()";

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 全局默认：不重试 */
    private final ErrorDecoder globalDecoder = new FeignConfig().feignErrorDecoder(objectMapper);
    /** 幂等读客户端：开启重试 */
    private final ErrorDecoder idempotentReadDecoder =
            new IdempotentReadFeignConfig().feignErrorDecoder(objectMapper);

    @Test
    void globalDecoderDoesNotRetryOnServerError() {
        Exception e = globalDecoder.decode(METHOD_KEY, response(503, "{\"code\":\"A0500\",\"message\":\"boom\"}"));

        assertThat(e).isInstanceOf(BizException.class);
    }

    @Test
    void idempotentReadDecoderMarksServerErrorRetryable() {
        Exception e = idempotentReadDecoder.decode(METHOD_KEY,
                response(503, "{\"code\":\"A0500\",\"message\":\"boom\"}"));

        assertThat(e).isInstanceOf(RetryableException.class);
    }

    @Test
    void bothTreatClientErrorAsBusinessErrorWithDownstreamCode() {
        Response response = response(403, "{\"code\":\"A0403\",\"message\":\"no permission\"}");

        Exception global = globalDecoder.decode(METHOD_KEY, response);
        Exception read = idempotentReadDecoder.decode(METHOD_KEY, response);

        assertThat(global).isInstanceOf(BizException.class);
        assertThat(((BizException) global).getCode()).isEqualTo("A0403");
        assertThat(read).isInstanceOf(BizException.class);
        assertThat(((BizException) read).getCode()).isEqualTo("A0403");
    }

    @Test
    void unparseableBodyFallsBackToDefaultCode() {
        Exception e = globalDecoder.decode(METHOD_KEY, response(400, "not-json"));

        assertThat(e).isInstanceOf(BizException.class);
    }

    private Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://link-auth/internal/ping",
                new HashMap<>(), null, StandardCharsets.UTF_8);
        return Response.builder()
                .status(status)
                .reason("x")
                .headers(new HashMap<>())
                .body(body, StandardCharsets.UTF_8)
                .request(request)
                .build();
    }
}
