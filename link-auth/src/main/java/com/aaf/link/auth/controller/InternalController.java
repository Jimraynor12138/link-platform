package com.aaf.link.auth.controller;

import com.aaf.link.auth.config.AuthProperties;
import com.aaf.link.auth.config.JwtProperties;
import com.aaf.link.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部接口（供服务间调用）。
 *
 * <p><b>P2 说明</b>：{@code /internal/ping} 是<b>探活接口，不是业务功能</b>，
 * 用于验证"注册发现 + Feign 调用"这条通路。
 *
 * <p><b>安全提醒</b>：{@code /internal/**} 目前被加进了 {@code link.auth.permit-all}（免认证放行），
 * 这是 P2 的临时处理。P3 网关上线后，内部接口**不应对外暴露**，应改为内网隔离或加内部鉴权。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${server.port:0}")
    private int port;

    /**
     * 探活：返回服务标识与少量非敏感配置（证明配置来自配置中心）
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", applicationName);
        data.put("instance", applicationName + ":" + port);
        data.put("timestamp", Instant.now().toString());
        // 非敏感配置回显：用于验证"配置中心改了值、不重启即生效"
        data.put("jwtExpiration", jwtProperties.getExpiration());
        data.put("permitAllSize", authProperties.getPermitAll().size());
        return Result.success(data);
    }
}
