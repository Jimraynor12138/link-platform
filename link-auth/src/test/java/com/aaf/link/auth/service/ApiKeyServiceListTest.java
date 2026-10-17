package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.ApiKeyMapper;
import com.aaf.link.auth.model.entity.ApiKey;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API Key 列表查询测试（P6）：按租户过滤 / 不传租户查全部。
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApiKeyServiceListTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyMapper);
    }

    @Test
    void listByTenantIdFiltersByTenant() {
        ApiKey key = new ApiKey();
        key.setTenantId(1L);
        key.setAccessKey("ak_test");
        when(apiKeyMapper.selectList(any())).thenReturn(List.of(key));

        List<ApiKey> result = apiKeyService.listByTenantId(1L);

        assertEquals(1, result.size());
        assertEquals("ak_test", result.get(0).getAccessKey());

        ArgumentCaptor<QueryWrapper<ApiKey>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(apiKeyMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("tenant_id"),
                "指定租户时必须带 tenant_id 条件");
    }

    @Test
    void listByTenantIdWithoutTenantQueriesAll() {
        when(apiKeyMapper.selectList(any())).thenReturn(List.of());

        assertTrue(apiKeyService.listByTenantId(null).isEmpty());

        ArgumentCaptor<QueryWrapper<ApiKey>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(apiKeyMapper).selectList(captor.capture());
        assertFalse(captor.getValue().getSqlSegment().contains("tenant_id"),
                "不传租户时不应有 tenant_id 过滤条件");
    }
}
