package com.aaf.link.gateway.filter;

import com.aaf.link.common.constant.GatewayAuthHeaders;
import com.aaf.link.common.result.Result;
import com.aaf.link.gateway.config.JwtVerifyProperties;
import com.aaf.link.gateway.token.GatewayTokenBlacklistChecker;
import com.aaf.link.gateway.token.JwtTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权过滤器（WebFlux GlobalFilter）。
 *
 * <p>只做<b>身份认证</b>（你是谁），不做<b>授权</b>（你能做什么）——授权仍由各服务用
 * {@code @PreAuthorize} + 实时权限完成。链路：
 * <ol>
 *   <li>白名单（登录/刷新/短链跳转）直接放行</li>
 *   <li>验签（签名 + type=access）+ 登出撤销（jti 黑名单）</li>
 *   <li><b>剥离客户端伪造的身份头</b>，再写入从 token 解析出的真实身份头</li>
 * </ol>
 *
 * <p><b>关键安全点</b>：必须先 {@code h.remove(...)} 再写——否则客户端传入的
 * {@code X-User-Id} 等头会被下游当成真实身份，等于任何人可伪造登录态。
 *
 * <p><b>局限</b>：本方案要求"下游只能被网关访问"。本地开发 8081/8082 仍可直连，
 * 直连 + 伪造头即可越权；真正的隔离靠 P7 网络层（只暴露网关端口）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private final JwtTokenVerifier verifier;
    private final JwtVerifyProperties properties;
    private final GatewayTokenBlacklistChecker blacklistChecker;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1) 白名单：登录/刷新（精确）+ 短链跳转（单段字母数字）
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        // 2) 验签 + 黑名单
        String token = verifier.resolveToken(request);
        Claims claims = token == null ? null : verifier.verifyAccessToken(token);
        if (claims == null) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "A0401", "未认证或登录已过期");
        }
        if (blacklistChecker.isBlacklisted(claims.getId())) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "A0401", "令牌已失效，请重新登录");
        }

        // 3) 剥离伪造头 + 写真实身份头
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(GatewayAuthHeaders.USER_ID);
                    headers.remove(GatewayAuthHeaders.USER_NAME);
                    headers.remove(GatewayAuthHeaders.TENANT_ID);
                })
                .header(GatewayAuthHeaders.USER_ID, String.valueOf(claims.get("userId", Number.class)))
                .header(GatewayAuthHeaders.USER_NAME, claims.getSubject())
                .header(GatewayAuthHeaders.TENANT_ID, String.valueOf(claims.get("tenantId", Number.class)))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path) {
        if (properties.getWhitelist().contains(path)) {
            return true;
        }
        // /open/** 是第三方 API 入口：签名校验在 core 做，网关只放行（不要求 JWT）
        if (path.startsWith("/open/")) {
            return true;
        }
        // 短链跳转：根路径单段字母数字，长度与 core 的 RedirectController 对齐（1~16 位），
        // 避免把超长单段路径也当公开跳转放行（String.matches 用的是 Java 正则，支持 {1,16}）
        return path.matches("/[0-9a-zA-Z]{1,16}");
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Result.failure(code, message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.warn("写网关鉴权响应失败: {}", e.getMessage());
            return response.setComplete();
        }
    }
}
