package com.aaf.link.core.dao;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aaf.link.core.entity.LinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 短链接 Mapper。
 *
 * <p>跳转链路按 code 查短码是"访客/无租户上下文"查询，必须豁免多租户过滤（短码全局唯一）。
 * 因此下面两个自定义方法显式标注 {@code @InterceptorIgnore(tenantLine = "true")}；
 * 其余 BaseMapper 方法（分页/插入/按 id 改删）走拦截器的租户过滤。
 */
public interface LinkMapper extends BaseMapper<LinkDO> {

    /**
     * 跳转/缓存回源：按 code + domain 查短链（豁免租户过滤，短码全局唯一）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM t_link WHERE code = #{code} AND domain = #{domain} AND del_flag = 0 LIMIT 1")
    LinkDO selectByCodeAndDomain(@Param("code") String code, @Param("domain") String domain);

    /**
     * 统计落库回源：按 code 批量查短链（豁免租户过滤，定时任务无租户上下文）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT * FROM t_link WHERE domain = #{domain} AND del_flag = 0 AND code IN "
            + "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach></script>")
    List<LinkDO> selectByCodeIn(@Param("domain") String domain, @Param("codes") List<String> codes);
}
