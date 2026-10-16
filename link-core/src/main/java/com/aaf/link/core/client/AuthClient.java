package com.aaf.link.core.client;

import com.aaf.link.common.dto.ApiKeyInfo;
import com.aaf.link.common.dto.TenantInfo;
import com.aaf.link.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 调用 link-auth 的 Feign 客户端。
 *
 * <p>P2 只有探活接口；P4 起升级为<b>真实业务调用</b>：查租户配额、验 API Key。
 *
 * <p>用 {@code name = "link-auth"}（服务名）而不是 {@code url}，才会走注册发现 + 负载均衡。
 *
 * <p>{@code configuration = IdempotentReadFeignConfig.class}：本客户端只做幂等读，开启"5xx 重试"；
 * 写接口接入前必须移到不重试的配置下（P2-3/P2-4 安全默认）。
 */
@FeignClient(name = "link-auth", path = "/internal",
        configuration = IdempotentReadFeignConfig.class,
        fallback = AuthClientFallback.class)
public interface AuthClient {

    /**
     * 探测下游是否可达（非业务功能）
     */
    @GetMapping("/ping")
    Result<Map<String, Object>> ping();

    /**
     * 查租户信息（状态 + 每日配额）
     */
    @GetMapping("/tenants/{id}")
    Result<TenantInfo> getTenant(@PathVariable("id") Long id);

    /**
     * 按 access_key 查凭据信息（secret/状态/配额/过期），用于验签与配额
     */
    @GetMapping("/api-keys/{accessKey}")
    Result<ApiKeyInfo> getApiKey(@PathVariable("accessKey") String accessKey);
}
