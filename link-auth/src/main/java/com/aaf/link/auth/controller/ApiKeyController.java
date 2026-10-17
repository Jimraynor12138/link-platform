package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.ApiKeyCreateReqDTO;
import com.aaf.link.auth.model.entity.ApiKey;
import com.aaf.link.auth.model.vo.ApiKeyCreateVO;
import com.aaf.link.auth.model.vo.ApiKeyVO;
import com.aaf.link.auth.service.ApiKeyService;
import com.aaf.link.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放接口凭据管理（P4 生成 / P6 列表）。
 *
 * <p>生成与查询都需要 {@code tenant:manage} 权限（平台管理员）——
 * secret 生成后只展示一次；列表接口<b>不返回 secret</b>。
 */
@RestController
@RequestMapping("/auth/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:manage')")
    public Result<ApiKeyCreateVO> create(@RequestBody @Valid ApiKeyCreateReqDTO reqDTO) {
        ApiKey key = apiKeyService.generate(reqDTO.getTenantId(), reqDTO.getQuota(), reqDTO.getExpireAt());
        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.setAccessKey(key.getAccessKey());
        vo.setSecret(key.getSecret());
        vo.setNotice("secret 仅此一次可见，请妥善保存");
        return Result.success(vo);
    }

    /**
     * 按租户查询 API Key 列表（tenantId 为空则查全部）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:manage')")
    public Result<List<ApiKeyVO>> list(@RequestParam(required = false) Long tenantId) {
        return Result.success(apiKeyService.listByTenantId(tenantId).stream().map(this::toVO).toList());
    }

    private ApiKeyVO toVO(ApiKey key) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setAccessKey(key.getAccessKey());
        vo.setQuota(key.getQuota());
        vo.setExpireAt(key.getExpireAt());
        vo.setStatus(key.getStatus());
        vo.setCreateTime(key.getCreateTime());
        return vo;
    }
}
