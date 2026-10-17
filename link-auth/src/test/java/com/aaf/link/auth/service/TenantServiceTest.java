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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户服务测试（P6）：列表透传 + 开通租户的事务性写入。
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantMapper, roleMapper, rolePermissionMapper,
                permissionMapper, userMapper, userRoleMapper, passwordEncoder);
    }

    @Test
    void listAllReturnsMapperResult() {
        Tenant demo = tenant(1L, "演示租户", 1, 100000L);
        Tenant second = tenant(2L, "租户二", 1, 50000L);
        when(tenantMapper.selectList(any())).thenReturn(List.of(demo, second));

        List<Tenant> result = tenantService.listAll();

        assertEquals(2, result.size());
        assertEquals("演示租户", result.get(0).getName());
        verify(tenantMapper).selectList(any());
    }

    @Test
    void listAllReturnsEmptyWhenNoTenant() {
        when(tenantMapper.selectList(any())).thenReturn(List.of());

        assertTrue(tenantService.listAll().isEmpty());
    }

    @Test
    void createBuildsTenantRoleAdminAndBindings() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.selectList(any()))
                .thenReturn(List.of(permission(3L, "link:manage"), permission(5L, "stats:read")));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");

        TenantCreateReqDTO req = new TenantCreateReqDTO();
        req.setName("租户三");
        req.setAdminUsername("tenant3admin");
        req.setAdminPassword("123456");

        TenantCreateVO vo = tenantService.create(req);

        assertNotNull(vo);
        assertEquals("tenant3admin", vo.getAdminUsername());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        assertEquals("租户三", tenantCaptor.getValue().getName());
        assertEquals(1, tenantCaptor.getValue().getStatus().intValue());
        assertEquals(100_000L, tenantCaptor.getValue().getQuotaPerDay().longValue());

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleMapper).insert(roleCaptor.capture());
        assertEquals("TENANT_ADMIN", roleCaptor.getValue().getCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("tenant3admin", userCaptor.getValue().getUsername());
        assertEquals("encoded", userCaptor.getValue().getPassword());

        verify(rolePermissionMapper, times(2)).insert(any(RolePermission.class));
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    void createRejectsDuplicateTenantName() {
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        TenantCreateReqDTO req = new TenantCreateReqDTO();
        req.setName("已有租户");
        req.setAdminUsername("newadmin");
        req.setAdminPassword("123456");

        BizException e = assertThrows(BizException.class, () -> tenantService.create(req));
        assertEquals("A0409", e.getCode());
        verify(tenantMapper, never()).insert(any(Tenant.class));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void createRejectsDuplicateAdminUsername() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(1L);

        TenantCreateReqDTO req = new TenantCreateReqDTO();
        req.setName("新租户");
        req.setAdminUsername("tenantadmin");
        req.setAdminPassword("123456");

        BizException e = assertThrows(BizException.class, () -> tenantService.create(req));
        assertEquals("A0409", e.getCode());
        verify(tenantMapper, never()).insert(any(Tenant.class));
    }

    private Tenant tenant(Long id, String name, Integer status, Long quotaPerDay) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName(name);
        tenant.setStatus(status);
        tenant.setQuotaPerDay(quotaPerDay);
        return tenant;
    }

    private Permission permission(Long id, String code) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        return permission;
    }
}
