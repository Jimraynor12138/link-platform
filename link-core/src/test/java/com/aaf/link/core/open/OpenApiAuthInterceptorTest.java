package com.aaf.link.core.open;

import com.aaf.link.common.context.TenantContext;
import com.aaf.link.common.dto.ApiKeyInfo;
import com.aaf.link.common.result.Result;
import com.aaf.link.core.client.AuthClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 开放接口签名校验单测：有效签名 / 缺头 / 错签 / nonce 重放 / 配额超限。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAuthInterceptorTest {

    private static final String ACCESS_KEY = "ak_test";
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Mock
    private AuthClient authClient;
    @Mock
    private ObjectProvider<AuthClient> authClientProvider;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ApiQuotaService quotaService;

    private OpenApiAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new OpenApiAuthInterceptor(authClientProvider, stringRedisTemplate, new ObjectMapper(), quotaService);

        ApiKeyInfo info = new ApiKeyInfo();
        info.setTenantId(1L);
        info.setAccessKey(ACCESS_KEY);
        info.setSecret(SECRET);
        info.setQuota(100000L);
        info.setStatus(1);
        lenient().when(authClientProvider.getObject()).thenReturn(authClient);
        lenient().when(authClient.getApiKey(ACCESS_KEY)).thenReturn(Result.success(info));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(quotaService.tryAcquire(anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void validSignaturePassesAndInjectsTenant() {
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = signedRequest(timestamp, "nonce-1", sign(ACCESS_KEY, timestamp, "nonce-1"));

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(1L);
    }

    @Test
    void missingHeaderRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidSignatureRejected() {
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = signedRequest(timestamp, "nonce-2", "bad-sign");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void replayedNonceRejected() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = signedRequest(timestamp, "nonce-3", sign(ACCESS_KEY, timestamp, "nonce-3"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void quotaExceededRejected() {
        when(quotaService.tryAcquire(anyString(), any())).thenReturn(false);
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = signedRequest(timestamp, "nonce-4", sign(ACCESS_KEY, timestamp, "nonce-4"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest signedRequest(long timestamp, String nonce, String sign) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Access-Key", ACCESS_KEY);
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Sign", sign);
        return request;
    }

    private String sign(String accessKey, long timestamp, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String data = accessKey + "\n" + timestamp + "\n" + nonce;
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
