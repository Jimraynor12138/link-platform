package com.aaf.link.gateway.filter;

import com.aaf.link.common.constant.GatewayAuthHeaders;
import com.aaf.link.gateway.config.JwtVerifyProperties;
import com.aaf.link.gateway.token.GatewayTokenBlacklistChecker;
import com.aaf.link.gateway.token.JwtTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关鉴权过滤器单测。
 *
 * <p>核心断言：白名单放行、无 token/非法/黑名单 → 401、<b>伪造身份头被剥离并覆盖为真实身份</b>。
 */
@ExtendWith(MockitoExtension.class)
class GatewayAuthFilterTest {

    @Mock
    private JwtTokenVerifier verifier;
    @Mock
    private GatewayTokenBlacklistChecker blacklistChecker;
    @Mock
    private GatewayFilterChain chain;

    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtVerifyProperties properties = new JwtVerifyProperties();
        properties.setSecret("unit-test-secret-key-0123456789abcdefghijklmn");
        filter = new GatewayAuthFilter(verifier, properties, blacklistChecker, new ObjectMapper());
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void whitelistedPathPassesThrough() {
        filter.filter(exchange("/auth/login"), chain).block();

        verify(chain).filter(any());
    }

    @Test
    void redirectSingleSegmentIsPublic() {
        filter.filter(exchange("/abc123"), chain).block();

        verify(chain).filter(any());
    }

    @Test
    void missingTokenReturns401() {
        when(verifier.resolveToken(any())).thenReturn(null);

        ServerWebExchange exchange = exchange("/auth/me");
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenReturns401() {
        when(verifier.resolveToken(any())).thenReturn("bad-token");
        when(verifier.verifyAccessToken("bad-token")).thenReturn(null);

        ServerWebExchange exchange = exchange("/auth/me");
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void blacklistedTokenReturns401() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-revoked");
        when(verifier.resolveToken(any())).thenReturn("t");
        when(verifier.verifyAccessToken("t")).thenReturn(claims);
        when(blacklistChecker.isBlacklisted("jti-revoked")).thenReturn(true);

        ServerWebExchange exchange = exchange("/auth/me");
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgedIdentityHeadersAreStrippedAndRewritten() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("tenantadmin");
        when(claims.get("userId", Number.class)).thenReturn(2L);
        when(claims.get("tenantId", Number.class)).thenReturn(1L);
        when(verifier.resolveToken(any())).thenReturn("t");
        when(verifier.verifyAccessToken("t")).thenReturn(claims);
        when(blacklistChecker.isBlacklisted("jti-1")).thenReturn(false);

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/auth/me");
        builder.header("Authorization", "Bearer t");
        // 客户端伪造的身份头，必须被剥离并覆盖
        builder.header(GatewayAuthHeaders.USER_ID, "999");
        builder.header(GatewayAuthHeaders.USER_NAME, "hacker");
        builder.header(GatewayAuthHeaders.TENANT_ID, "999");
        ServerWebExchange exchange = MockServerWebExchange.from(builder.build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = captured.get().getRequest();
        assertThat(forwarded.getHeaders().getFirst(GatewayAuthHeaders.USER_ID)).isEqualTo("2");
        assertThat(forwarded.getHeaders().getFirst(GatewayAuthHeaders.USER_NAME)).isEqualTo("tenantadmin");
        assertThat(forwarded.getHeaders().getFirst(GatewayAuthHeaders.TENANT_ID)).isEqualTo("1");
    }

    private ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
