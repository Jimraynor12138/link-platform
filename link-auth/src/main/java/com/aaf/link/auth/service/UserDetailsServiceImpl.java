package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.UserMapper;
import com.aaf.link.auth.model.dto.LoginUser;
import com.aaf.link.auth.model.entity.User;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 加载用户与权限，组装认证主体 {@link LoginUser}。
 *
 * <p>每次调用都实时查库（含角色码与权限码），保证：
 * 禁用状态即时生效、权限变更即时生效。代价是每个请求一次 DB 查询 ——
 * 这是"新鲜度 vs DB 负载"的有意取舍（若日后成为瓶颈，可改为直取令牌声明 + 短 TTL 本地缓存）。
 *
 * <p>禁用用户这里只把 {@code enabled} 置为 false，不直接抛异常：
 * 走 AuthenticationManager 时由 Spring Security 抛出 DisabledException；
 * 刷新令牌流程则显式判断 enabled —— 两条路径都能拦住禁用用户。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());
        loginUser.setNickname(user.getNickname());
        loginUser.setEnabled(user.getStatus() != null && user.getStatus() == 1);
        loginUser.setRoles(userMapper.selectRoleCodesByUserId(user.getId()));
        loginUser.setPermissions(userMapper.selectPermissionCodesByUserId(user.getId()));
        return loginUser;
    }
}
