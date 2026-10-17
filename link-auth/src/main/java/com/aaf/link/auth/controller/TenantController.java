package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.TenantCreateReqDTO;
import com.aaf.link.auth.model.entity.Tenant;
import com.aaf.link.auth.model.vo.TenantCreateVO;
import com.aaf.link.auth.model.vo.TenantVO;
import com.aaf.link.auth.service.TenantService;
import com.aaf.link.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理接口（P6 平台侧）。
 *
 * <p>查询与开通都要求 {@code tenant:manage}（平台管理员）。
 * 开通时同时创建该租户的初始管理员，避免"建了租户没人能登录"。
 */
@RestController
@RequestMapping("/auth/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:manage')")
    public Result<List<TenantVO>> list() {
        return Result.success(tenantService.listAll().stream().map(this::toVO).toList());
    }

    /**
     * 开通新租户（含租户管理员账号）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:manage')")
    public Result<TenantCreateVO> create(@RequestBody @Valid TenantCreateReqDTO reqDTO) {
        return Result.success(tenantService.create(reqDTO));
    }

    private TenantVO toVO(Tenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setStatus(tenant.getStatus());
        vo.setQuotaPerDay(tenant.getQuotaPerDay());
        return vo;
    }
}
