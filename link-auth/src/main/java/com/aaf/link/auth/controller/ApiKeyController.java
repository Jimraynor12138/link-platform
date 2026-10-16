package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.ApiKeyCreateReqDTO;
import com.aaf.link.auth.model.entity.ApiKey;
import com.aaf.link.auth.model.vo.ApiKeyCreateVO;
import com.aaf.link.auth.service.ApiKeyService;
import com.aaf.link.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放接口凭据管理（P4）。
 *
 * <p>生成 API Key 需要 {@code tenant:manage} 权限（平台管理员）——secret 生成后只展示一次。
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
}
