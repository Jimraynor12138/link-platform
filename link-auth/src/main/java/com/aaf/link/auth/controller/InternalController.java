package com.aaf.link.auth.controller;

import com.aaf.link.auth.config.AuthProperties;
import com.aaf.link.auth.config.JwtProperties;
import com.aaf.link.auth.mapper.ApiKeyMapper;
import com.aaf.link.auth.mapper.TenantMapper;
import com.aaf.link.auth.model.entity.ApiKey;
import com.aaf.link.auth.model.entity.Tenant;
import com.aaf.link.common.dto.ApiKeyInfo;
import com.aaf.link.common.dto.TenantInfo;
import com.aaf.link.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部接口（供服务间调用）。
 *
 * <p>{@code /internal/ping} 是探活接口（非业务功能）；{@code /internal/tenants/{id}} 与
 * {@code /internal/api-keys/{accessKey}} 是 P4 起 core 通过 Feign 调用的<b>真实业务</b>接口
 * （查租户配额 / 验 API Key）。
 *
 * <p><b>安全提醒</b>：{@code /internal/**} 目前被加进了 {@code link.auth.permit-all}（免认证放行），
 * 依赖"下游只能被网关访问 / Feign 内网直连"这一前提（台账 P2-2/P3-1，P7 网络层收口）。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final TenantMapper tenantMapper;
    private final ApiKeyMapper apiKeyMapper;

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
        data.put("jwtExpiration", jwtProperties.getExpiration());
        data.put("permitAllSize", authProperties.getPermitAll().size());
        return Result.success(data);
    }

    /**
     * 查租户信息（状态 + 每日配额），供 core 创建短链前校验配额
     */
    @GetMapping("/tenants/{id}")
    public Result<TenantInfo> getTenant(@PathVariable("id") Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            return Result.failure("A0404", "租户不存在");
        }
        TenantInfo info = new TenantInfo();
        info.setId(tenant.getId());
        info.setName(tenant.getName());
        info.setStatus(tenant.getStatus());
        info.setQuotaPerDay(tenant.getQuotaPerDay());
        return Result.success(info);
    }

    /**
     * 按 access_key 查凭据信息（secret/状态/配额/过期），供 core 验签与配额扣减。
     * 注意：secret 只在内网服务间传递，不对外暴露。
     */
    @GetMapping("/api-keys/{accessKey}")
    public Result<ApiKeyInfo> getApiKey(@PathVariable("accessKey") String accessKey) {
        ApiKey key = apiKeyMapper.selectOne(new QueryWrapper<ApiKey>()
                .eq("access_key", accessKey).last("LIMIT 1"));
        if (key == null) {
            return Result.failure("A0404", "API Key 不存在");
        }
        ApiKeyInfo info = new ApiKeyInfo();
        info.setId(key.getId());
        info.setTenantId(key.getTenantId());
        info.setAccessKey(key.getAccessKey());
        info.setSecret(key.getSecret());
        info.setQuota(key.getQuota());
        info.setExpireAt(key.getExpireAt());
        info.setStatus(key.getStatus());
        return Result.success(info);
    }
}
