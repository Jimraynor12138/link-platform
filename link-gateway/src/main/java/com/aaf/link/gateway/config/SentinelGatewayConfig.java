package com.aaf.link.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sentinel 网关入口限流（代码加载规则，Dashboard 可观察/覆盖）。
 *
 * <p>对应"两层限流"的<b>网关层</b>：按路由做粗粒度 QPS 限流；服务层细粒度限流见
 * core 的 {@code SentinelRulesConfig} 与 {@code @SentinelResource}。
 *
 * <p><b>踩坑</b>：不显式注册 {@link SentinelGatewayBlockExceptionHandler} 时，被限流的请求
 * 响应会是"连接重置"（curl 显示 000），而不是清晰的 429。这里同时注册了拦截处理器 + JSON 响应体。
 */
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                 ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 拦截处理器：被限流时返回 429 + JSON（否则是连接重置）
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @PostConstruct
    public void init() {
        initBlockHandler();
        initFlowRules();
    }

    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((ServerWebExchange exchange, Throwable t) -> {
            Map<String, String> result = new HashMap<>();
            result.put("code", "A0429");
            result.put("message", "请求过于频繁，已被网关限流");
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(result));
        });
    }

    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        // 对 /api/v1/** 路由（route id = link-core-api）限流：QPS=50，1 秒统计窗口。
        // P6 从 2 调到 50：管理后台一页会并发拉列表/分组/统计，QPS=2 会把 UI 直接卡成 429。
        // 压测/演示"限流生效"时仍可临时调回 2。
        rules.add(new GatewayFlowRule("link-core-api").setCount(50).setIntervalSec(1));
        GatewayRuleManager.loadRules(rules);
    }
}
