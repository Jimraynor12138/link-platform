package com.aaf.link.auth.mapper;

import com.aaf.link.auth.model.entity.ApiKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 开放接口凭据 Mapper（P1 仅建表与映射，P4 启用签名校验与配额）。
 */
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
}
