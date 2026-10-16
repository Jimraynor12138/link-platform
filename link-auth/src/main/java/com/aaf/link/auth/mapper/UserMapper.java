package com.aaf.link.auth.mapper;

import com.aaf.link.auth.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。
 *
 * 说明：角色码与权限码用两条聚合查询取得（避免 N+1），均在 SQL 层过滤逻辑删除。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查用户拥有的角色码
     */
    @Select("SELECT r.code FROM t_role r "
            + "JOIN t_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.del_flag = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查用户拥有的权限码（经 用户-角色-权限 三表关联，去重）
     */
    @Select("SELECT DISTINCT p.code FROM t_permission p "
            + "JOIN t_role_permission rp ON rp.permission_id = p.id "
            + "JOIN t_role r ON r.id = rp.role_id AND r.del_flag = 0 "
            + "JOIN t_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
