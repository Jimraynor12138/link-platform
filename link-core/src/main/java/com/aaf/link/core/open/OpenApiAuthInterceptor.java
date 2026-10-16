package com.aaf.link.core.open;

import com.aaf.link.common.context.TenantContext;
import com.aaf.link.common.dto.ApiKeyInfo;
import com.aaf.link.common.result.Result;
import com.aaf.link.core.client.AuthClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 开放接口（/open/**）签名校验拦截器（P4）。
 *
 * <p>校验顺序：access_key 存在 → status 启用 → 未过期 → 时间窗（±5min）→ nonce 防重放 → 验签 → 配额。
 * 全部通过后把 API Key 的 tenant_id 注入 {@link TenantContext}，多租户拦截器据此过滤数据。
 *
 * <p>签名算法：{@code sign = HMAC-SHA256(secret, accessKey + "\n" + timestamp + "\n" + nonce)}（十六进制）。
 * <b>简化说明</b>：P4 只对请求头签名（未纳入请求体）；生产应把 body 也纳入签名以抗篡改（本阶段如实标注）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;
    private static final String NONCE_KEY_PREFIX = "link:core:open:nonce:";
    private static final String HEADER_ACCESS_KEY = "X-Access-Key";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGN = "X-Sign";

    private final AuthClient authClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiQuotaService quotaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accessKey = request.getHeader(HEADER_ACCESS_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String sign = request.getHeader(HEADER_SIGN);
        if (isBlank(accessKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "缺少签名参数");
        }

        // 1. 取凭据（Feign 调 auth；下游不可用即 fail-closed，见 AuthClientFallback）
        Result<ApiKeyInfo> keyResult = authClient.getApiKey(accessKey);
        ApiKeyInfo info = keyResult == null ? null : keyResult.getData();
        if (info == null) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "API Key 无效");
        }
        // 2. 状态 + 过期
        if (info.getStatus() == null || info.getStatus() != 1) {
            return writeError(response, HttpStatus.FORBIDDEN, "A0403", "API Key 已停用");
        }
        if (info.getExpireAt() != null && info.getExpireAt().isBefore(LocalDateTime.now())) {
            return writeError(response, HttpStatus.FORBIDDEN, "A0403", "API Key 已过期");
        }
        // 3. 时间窗
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "时间戳非法");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "时间戳超出窗口");
        }
        // 4. nonce 防重放
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + accessKey + ":" + nonce, "1",
                        Duration.ofMillis(TIMESTAMP_WINDOW_MS + 60_000));
        if (Boolean.FALSE.equals(first)) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "请求重复（nonce 已使用）");
        }
        // 5. 验签
        String expected = hmacSha256Hex(info.getSecret(), accessKey + "\n" + timestamp + "\n" + nonce);
        if (!expected.equalsIgnoreCase(sign)) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "A0401", "签名校验失败");
        }
        // 6. 配额
        if (!quotaService.tryAcquire(accessKey, info.getQuota())) {
            return writeError(response, HttpStatus.TOO_MANY_REQUESTS, "A0429", "调用配额已用完");
        }
        // 7. 注入租户上下文
        TenantContext.setTenantId(info.getTenantId());
        return true;
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private boolean writeError(HttpServletResponse response, HttpStatus status, String code, String message) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(Result.failure(code, message)));
        } catch (Exception e) {
            log.warn("写开放接口错误响应失败: {}", e.getMessage());
        }
        return false;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
