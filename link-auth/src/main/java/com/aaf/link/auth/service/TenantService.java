package com.aaf.link.auth.service;

import com.aaf.link.auth.mapper.TenantMapper;
import com.aaf.link.auth.model.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户查询服务（P6）。
 *
 * <p>{@link Tenant} 带 {@code @TableLogic del_flag}，MyBatis-Plus 会自动过滤已删除行，
 * 因此这里直接 {@code selectList(null)} 即可。
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    /**
     * 查询全部租户（按 id 升序，便于前端稳定展示）
     */
    public List<Tenant> listAll() {
        return tenantMapper.selectList(null);
    }
}
