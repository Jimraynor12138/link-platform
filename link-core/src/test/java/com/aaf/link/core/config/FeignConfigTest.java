package com.aaf.link.core.config;

import com.aaf.link.common.exception.BizException;
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
 * Feign 错误解码单测：验证"错误分类决定重试语义"。
 */
class FeignConfigTest {

    private final ErrorDecoder decoder = new FeignConfig().feignErrorDecoder(new ObjectMapper());

    @Test
    void serverErrorBecomesRetryable() {
        Response response = response(503, "{\"code\":\"A0500\",\"message\":\"boom\"}");

        Exception e = decoder.decode("AuthClient#ping()", response);

        assertThat(e).isInstanceOf(RetryableException.class);
    }

    @Test
    void clientErrorBecomesBizExceptionWithDownstreamCode() {
        Response response = response(403, "{\"code\":\"A0403\",\"message\":\"no permission\"}");

        Exception e = decoder.decode("AuthClient#ping()", response);

        assertThat(e).isInstanceOf(BizException.class);
        assertThat(((BizException) e).getCode()).isEqualTo("A0403");
    }

    @Test
    void clientErrorWithUnparseableBodyFallsBackToDefaultCode() {
        Response response = response(400, "not-json");

        Exception e = decoder.decode("AuthClient#ping()", response);

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
