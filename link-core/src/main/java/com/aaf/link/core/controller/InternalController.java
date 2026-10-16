package com.aaf.link.core.controller;

import com.aaf.link.common.result.Result;
import com.aaf.link.core.client.AuthClient;
import com.aaf.link.core.config.LinkCoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部接口（验证服务间调用与配置中心动态刷新）。
 *
 * <p><b>P2 说明</b>：这里的两个接口都<b>不是业务功能</b>：
 * <ul>
 *   <li>{@code /internal/ping}：回显服务标识与<b>非敏感</b>配置，用于验证"配置中心改了值、不重启即生效"</li>
 *   <li>{@code /internal/feign-ping}：通过 Feign 调用 link-auth 的探活接口，验证注册发现 + 服务间调用通路</li>
 * </ul>
 * 真实业务调用（校验租户配额等）在 P4 才出现。
 *
 * <p>回显的配置项刻意只选非敏感值（TTL / 限流阈值 / 域名 / 发号器类型），不涉及任何凭据。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final LinkCoreProperties properties;
    private final AuthClient authClient;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${server.port:0}")
    private int port;

    /**
     * 探活 + 配置回显
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", applicationName);
        data.put("instance", applicationName + ":" + port);
        data.put("timestamp", Instant.now().toString());
        // 非敏感配置回显：可动态刷新的项（改 Nacos 后重新调用即可看到新值）
        data.put("domain", properties.getDomain());
        data.put("idGeneratorType", properties.getIdGeneratorType());
        data.put("cacheTtlSeconds", properties.getCache().getTtlSeconds());
        data.put("rateLimitPerWindow", properties.getRateLimit().getWriteLimitPerWindow());
        return Result.success(data);
    }

    /**
     * 通过 Feign 调用 link-auth，验证服务间调用链路
     */
    @GetMapping("/feign-ping")
    public Result<Map<String, Object>> feignPing() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caller", applicationName + ":" + port);
        data.put("callee", authClient.ping().getData());
        return Result.success(data);
    }
}
