package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.ApiKeyMapper;
import com.aaf.link.auth.model.entity.ApiKey;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 开放接口凭据服务（P4）：生成 access_key + secret。
 *
 * <p>secret 是 HMAC 签名密钥，<b>必须存可检索形式</b>（否则 core 无法验签）——
 * 与"密码存摘要"不同，API Key 的 secret 是"双方共享的对称密钥"。
 * 生产应加密存储（如 KMS/信封加密），本项目为学习用途存明文并如实标注。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final long DEFAULT_QUOTA = 100_000L;

    private final ApiKeyMapper apiKeyMapper;

    /**
     * 生成一个 API Key（secret 仅在此次返回中可见）
     */
    public ApiKey generate(Long tenantId, Long quota, LocalDateTime expireAt) {
        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setAccessKey("ak_" + randomHex(12));
        key.setSecret(randomHex(32));
        key.setQuota(quota == null || quota <= 0 ? DEFAULT_QUOTA : quota);
        key.setExpireAt(expireAt);
        key.setStatus(1);
        apiKeyMapper.insert(key);
        return key;
    }

    /**
     * 按租户查询 API Key 列表（P6 平台侧）。
     *
     * @param tenantId 为空则查询全部租户（走 {@code idx_tenant} 索引）
     */
    public List<ApiKey> listByTenantId(Long tenantId) {
        QueryWrapper<ApiKey> wrapper = new QueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        wrapper.orderByDesc("create_time");
        return apiKeyMapper.selectList(wrapper);
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
