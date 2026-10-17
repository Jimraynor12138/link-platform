package com.aaf.link.auth.controller;

import com.aaf.link.auth.model.dto.ApiKeyCreateReqDTO;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.entity.ApiKey;
import com.aaf.link.auth.model.vo.ApiKeyCreateVO;
import com.aaf.link.auth.model.vo.ApiKeyVO;
import com.aaf.link.auth.service.ApiKeyService;
import com.aaf.link.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API Key 的租户自助边界测试（P6-4）。
 *
 * <p>核心规则：平台可跨租户；租户管理员只能作用于本租户，指定其他租户直接 403。
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(apiKeyService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantAdminCreatesKeyForOwnTenantWhenTenantIdMissing() {
        loginAs(1L, List.of("apikey:manage"));
        when(apiKeyService.generate(eq(1L), any(), any())).thenReturn(key("ak_1", "secret"));

        ApiKeyCreateReqDTO req = new ApiKeyCreateReqDTO();
        req.setTenantId(null);
        req.setQuota(1000L);

        Result<ApiKeyCreateVO> result = controller.create(req);

        assertTrue(result.isSuccess());
        verify(apiKeyService).generate(1L, 1000L, null);
    }

    @Test
    void tenantAdminCannotCreateKeyForAnotherTenant() {
        loginAs(1L, List.of("apikey:manage"));

        ApiKeyCreateReqDTO req = new ApiKeyCreateReqDTO();
        req.setTenantId(2L);

        assertThrows(AccessDeniedException.class, () -> controller.create(req));
        verify(apiKeyService, never()).generate(anyLong(), any(), any());
    }

    @Test
    void tenantAdminListIsForcedToOwnTenant() {
        loginAs(1L, List.of("apikey:manage"));
        when(apiKeyService.listByTenantId(1L)).thenReturn(List.of(key("ak_1", "s")));

        Result<List<ApiKeyVO>> result = controller.list(2L);

        assertTrue(result.isSuccess());
        verify(apiKeyService).listByTenantId(1L);
    }

    @Test
    void platformAdminCanListAllTenants() {
        loginAs(0L, List.of("tenant:manage"));
        when(apiKeyService.listByTenantId(null)).thenReturn(List.of());

        controller.list(null);

        verify(apiKeyService).listByTenantId(null);
    }

    @Test
    void platformCreateWithoutTenantIdReturnsFailure() {
        loginAs(0L, List.of("tenant:manage"));

        Result<ApiKeyCreateVO> result = controller.create(new ApiKeyCreateReqDTO());

        assertFalse(result.isSuccess());
        assertEquals("A0400", result.getCode());
    }

    private void loginAs(Long tenantId, List<String> permissions) {
        LoginUser user = new LoginUser();
        user.setId(10L);
        user.setUsername("tester");
        user.setTenantId(tenantId);
        user.setPermissions(permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private ApiKey key(String accessKey, String secret) {
        ApiKey key = new ApiKey();
        key.setAccessKey(accessKey);
        key.setSecret(secret);
        key.setQuota(1000L);
        key.setStatus(1);
        key.setTenantId(1L);
        return key;
    }
}
