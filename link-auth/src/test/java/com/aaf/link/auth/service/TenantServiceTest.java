package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.TenantMapper;
import com.aaf.link.auth.model.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户查询服务测试（P6）：列表透传 mapper 结果。
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantMapper tenantMapper;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantMapper);
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

    private Tenant tenant(Long id, String name, Integer status, Long quotaPerDay) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName(name);
        tenant.setStatus(status);
        tenant.setQuotaPerDay(quotaPerDay);
        return tenant;
    }
}
