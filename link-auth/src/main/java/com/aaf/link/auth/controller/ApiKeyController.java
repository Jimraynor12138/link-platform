package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.ApiKeyCreateReqDTO;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.entity.ApiKey;
import com.aaf.link.auth.model.vo.ApiKeyCreateVO;
import com.aaf.link.auth.model.vo.ApiKeyVO;
import com.aaf.link.auth.security.SecurityUtils;
import com.aaf.link.auth.service.ApiKeyService;
import com.aaf.link.common.constant.CommonConstants;
import com.aaf.link.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放接口凭据管理（P4 生成 / P6 列表 + 租户自助）。
 *
 * <p>权限模型（P6-4 修复）：
 * <ul>
 *   <li>平台管理员（{@code tenant:manage}）——可查看/创建<b>任意租户</b>的 Key；</li>
 *   <li>租户管理员（{@code apikey:manage}）——只能查看/创建<b>本租户</b>的 Key，
 *       若显式指定其他租户直接 403；</li>
 *   <li>只读用户——两者都没有，403。</li>
 * </ul>
 *
 * <p>secret 只在生成时返回一次；列表接口<b>不返回 secret</b>。
 */
@RestController
@RequestMapping("/auth/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('tenant:manage','apikey:manage')")
    public Result<ApiKeyCreateVO> create(@RequestBody @Valid ApiKeyCreateReqDTO reqDTO) {
        LoginUser user = currentUser();
        Long tenantId = resolveTargetTenantId(user, reqDTO.getTenantId());
        if (tenantId == null) {
            // 平台可以跨租户，但必须明确指定目标租户，避免"平台默认给自己建 key"的歧义
            return Result.failure("A0400", "平台创建 API Key 必须指定 tenantId");
        }

        ApiKey key = apiKeyService.generate(tenantId, reqDTO.getQuota(), reqDTO.getExpireAt());
        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.setAccessKey(key.getAccessKey());
        vo.setSecret(key.getSecret());
        vo.setNotice("secret 仅此一次可见，请妥善保存");
        return Result.success(vo);
    }

    /**
     * 列表：平台可查任意租户（不传 tenantId 查全部）；租户管理员固定查本租户。
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('tenant:manage','apikey:manage')")
    public Result<List<ApiKeyVO>> list(@RequestParam(required = false) Long tenantId) {
        LoginUser user = currentUser();
        Long scope = isPlatform(user) ? tenantId : user.getTenantId();
        return Result.success(apiKeyService.listByTenantId(scope).stream().map(this::toVO).toList());
    }

    private LoginUser currentUser() {
        LoginUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new AccessDeniedException("未登录");
        }
        return user;
    }

    /**
     * 平台级判定：tenantId=0，或持有跨租户权限 {@code tenant:manage}。
     * 后者兼容未来"平台账号挂在某租户下但拥有跨租户权限"的情况。
     */
    private boolean isPlatform(LoginUser user) {
        boolean isPlatformTenant = Long.valueOf(CommonConstants.PLATFORM_TENANT_ID).equals(user.getTenantId());
        boolean hasCrossTenantPermission = user.getPermissions() != null
                && user.getPermissions().contains("tenant:manage");
        return isPlatformTenant || hasCrossTenantPermission;
    }

    /**
     * 计算实际作用的租户：平台用户按入参；租户用户强制本租户，且不允许指定其他租户。
     */
    private Long resolveTargetTenantId(LoginUser user, Long requestedTenantId) {
        if (isPlatform(user)) {
            return requestedTenantId;
        }
        if (requestedTenantId != null && !requestedTenantId.equals(user.getTenantId())) {
            throw new AccessDeniedException("不能为其他租户生成 API Key");
        }
        return user.getTenantId();
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
