package com.aaf.link.auth.model.dto;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体（本服务所有链路的公共契约）。
 *
 * <p>它同时是三种身份：认证结果（Authentication.principal）、令牌声明的来源（签发时写入 id/tenantId/username）、
 * 以及安全上下文载体（authorities 由 permissions 构建，供 {@code @PreAuthorize} 使用）。
 *
 * <p>关键字段 {@code tenantId}：0 表示平台级，&gt; 0 表示租户级 —— 多租户隔离的唯一可信来源。
 */
@Data
public class LoginUser implements UserDetails {

    private Long id;

    /** 所属租户：0=平台级 &gt;0=租户级 */
    private Long tenantId;

    private String username;

    private String password;

    private String nickname;

    /** 角色码，如 PLATFORM_ADMIN（前端展示用） */
    private List<String> roles;

    /** 权限码，如 user:manage（接口级鉴权用） */
    private List<String> permissions;

    /** 是否启用：对应 t_user.status，禁用用户无法登录、也无法刷新令牌 */
    private boolean enabled = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
