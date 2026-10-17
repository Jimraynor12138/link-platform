package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.PermissionMapper;
import com.aaf.link.auth.mapper.RoleMapper;
import com.aaf.link.auth.mapper.RolePermissionMapper;
import com.aaf.link.auth.mapper.TenantMapper;
import com.aaf.link.auth.mapper.UserMapper;
import com.aaf.link.auth.mapper.UserRoleMapper;
import com.aaf.link.auth.model.dto.TenantCreateReqDTO;
import com.aaf.link.auth.model.entity.Permission;
import com.aaf.link.auth.model.entity.Role;
import com.aaf.link.auth.model.entity.RolePermission;
import com.aaf.link.auth.model.entity.Tenant;
import com.aaf.link.auth.model.entity.User;
import com.aaf.link.auth.model.entity.UserRole;
import com.aaf.link.auth.model.vo.TenantCreateVO;
import com.aaf.link.common.exception.BizException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户服务（P6）：查询全部租户 + 平台开通新租户（含初始管理员）。
 *
 * <p>{@link Tenant} 带 {@code @TableLogic del_flag}，查询会自动过滤已删除行。
 *
 * <p>开通租户是一个<b>多表事务</b>：租户 → 管理员角色 → 角色权限 → 管理员账号 → 用户角色绑定，
 * 任何一步失败都整体回滚，避免出现"有租户没管理员"的半成品数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** 新租户默认日配额 */
    private static final long DEFAULT_QUOTA_PER_DAY = 100_000L;

    /**
     * 租户管理员权限模板：按<b>权限码</b>复制，不硬编码权限 id
     * （新增权限时只需改这里，不会因为 id 漂移而漏配）。
     *
     * <p>不含 {@code tenant:manage}——跨租户是平台专属能力。
     */
    private static final List<String> TENANT_ADMIN_PERMISSIONS =
            List.of("user:manage", "link:manage", "link:read", "stats:read", "apikey:manage");

    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询全部租户（平台侧）
     */
    public List<Tenant> listAll() {
        return tenantMapper.selectList(null);
    }

    /**
     * 开通新租户：租户 + 管理员角色 + 权限 + 管理员账号 + 角色绑定，全部在一个事务里。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantCreateVO create(TenantCreateReqDTO req) {
        String name = req.getName().trim();
        String username = req.getAdminUsername().trim();

        // 1) 唯一性：先查给友好提示；DB 唯一键（uk_name / uk_username）做并发兜底
        if (tenantMapper.selectCount(Wrappers.<Tenant>lambdaQuery().eq(Tenant::getName, name)) > 0) {
            throw new BizException("A0409", "租户名称已存在");
        }
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            throw new BizException("A0409", "管理员用户名已存在");
        }

        // 2) 租户
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setStatus(1);
        tenant.setQuotaPerDay(req.getQuotaPerDay() == null || req.getQuotaPerDay() <= 0
                ? DEFAULT_QUOTA_PER_DAY : req.getQuotaPerDay());
        tenantMapper.insert(tenant);

        // 3) 该租户的管理员角色（t_role 唯一键 = tenant_id + code，每个租户一份）
        Role role = new Role();
        role.setTenantId(tenant.getId());
        role.setCode("TENANT_ADMIN");
        role.setName("租户管理员");
        role.setRemark("创建租户时自动生成");
        roleMapper.insert(role);

        // 4) 按权限码复制权限
        List<Permission> permissions = permissionMapper.selectList(
                Wrappers.<Permission>lambdaQuery().in(Permission::getCode, TENANT_ADMIN_PERMISSIONS));
        if (permissions.size() != TENANT_ADMIN_PERMISSIONS.size()) {
            log.warn("租户管理员权限模板不完整：期望 {} 个，实际 {} 个",
                    TENANT_ADMIN_PERMISSIONS.size(), permissions.size());
        }
        for (Permission permission : permissions) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermissionMapper.insert(rolePermission);
        }

        // 5) 管理员账号（BCrypt，不存明文）
        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(req.getAdminPassword()));
        admin.setNickname(req.getAdminNickname() == null || req.getAdminNickname().isBlank()
                ? username : req.getAdminNickname().trim());
        admin.setStatus(1);
        userMapper.insert(admin);

        // 6) 绑定角色
        UserRole userRole = new UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);

        TenantCreateVO vo = new TenantCreateVO();
        vo.setTenantId(tenant.getId());
        vo.setTenantName(tenant.getName());
        vo.setAdminUsername(admin.getUsername());
        return vo;
    }
}
