package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.entity.Tenant;
import com.aaf.link.auth.model.vo.TenantVO;
import com.aaf.link.auth.service.TenantService;
import com.aaf.link.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理接口（P6 平台侧）。
 *
 * <p>只有平台管理员（具备 {@code tenant:manage}）能查看全部租户。
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

    private TenantVO toVO(Tenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setStatus(tenant.getStatus());
        vo.setQuotaPerDay(tenant.getQuotaPerDay());
        return vo;
    }
}
