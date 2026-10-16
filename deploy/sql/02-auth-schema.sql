-- ============================================================
-- 多租户营销短链平台 · 认证授权表（V1 / P1）
-- 库：link_platform（与 core 共享库共享表）
--
-- 说明：
--   1) t_user 采用 DROP + 重建：link_platform 库中原 AAE 极简 t_user 未被 core 代码引用，
--      且库内为空，重建比 ALTER 干净。
--   2) tenant_id = 0 表示"平台级"（见 CommonConstants.PLATFORM_TENANT_ID），不建租户行。
--   3) 简化取舍：用户名全局唯一（uk_username）。若要支持"不同租户同名用户"，
--      需改为 uk_tenant_username 且登录时携带租户标识 —— 留待后续版本。
--   4) 种子密码均为 123456（BCrypt 摘要，已用 BCryptPasswordEncoder.matches 验证通过）。
-- 执行方式：mysql -uroot -p < deploy/sql/02-auth-schema.sql
-- ============================================================
USE `link_platform`;

-- ------------------------------------------------------------
-- 1. 租户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_tenant`;
CREATE TABLE `t_tenant` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    `name`          VARCHAR(64) NOT NULL COMMENT '租户名称',
    `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1正常 0停用',
    `quota_per_day` BIGINT      NOT NULL DEFAULT 100000 COMMENT '每日创建短链配额',
    `del_flag`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户表';

-- ------------------------------------------------------------
-- 2. 用户表（重建；tenant_id 贯穿多租户隔离）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '所属租户：0=平台级 >0=租户级',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 摘要）',
    `nickname`    VARCHAR(64)  NULL COMMENT '昵称',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表（多租户）';

-- ------------------------------------------------------------
-- 3. 角色表（tenant_id=0 为平台角色，>0 为租户角色）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `tenant_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '所属租户：0=平台角色 >0=租户角色',
    `code`        VARCHAR(64) NOT NULL COMMENT '角色码，如 PLATFORM_ADMIN',
    `name`        VARCHAR(64) NOT NULL COMMENT '角色名称',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `del_flag`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ------------------------------------------------------------
-- 4. 用户-角色关联
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联表';

-- ------------------------------------------------------------
-- 5. 权限表（权限码，用于接口级访问控制）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `code`        VARCHAR(64) NOT NULL COMMENT '权限码，如 user:manage',
    `name`        VARCHAR(64) NOT NULL COMMENT '权限名称',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限表';

-- ------------------------------------------------------------
-- 6. 角色-权限关联
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色权限关联表';

-- ------------------------------------------------------------
-- 7. 开放接口凭据表（P1 仅建表，签名校验与配额限流在 P4 实现）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_api_key`;
CREATE TABLE `t_api_key` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NOT NULL COMMENT '所属租户',
    `access_key`  VARCHAR(64)  NOT NULL COMMENT '访问标识（公开）',
    `secret`      VARCHAR(128) NOT NULL COMMENT '密钥（签名用，不对外暴露）',
    `quota`       BIGINT       NOT NULL DEFAULT 100000 COMMENT '调用配额',
    `expire_at`   DATETIME     NULL COMMENT '过期时间，NULL 表示永久',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_access_key` (`access_key`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '开放接口凭据表（P4 启用）';

-- ============================================================
-- 种子数据（本地开发/演示用；种子密码统一为 123456）
-- ============================================================

-- 租户：1 个演示租户（平台级 tenant_id=0 不建行）
INSERT INTO `t_tenant` (`id`, `name`, `status`, `quota_per_day`)
VALUES (1, '演示租户', 1, 100000);

-- 权限
INSERT INTO `t_permission` (`id`, `code`, `name`) VALUES
(1, 'tenant:manage', '租户管理'),
(2, 'user:manage',   '用户管理'),
(3, 'link:manage',   '短链管理'),
(4, 'link:read',     '短链只读'),
(5, 'stats:read',    '统计查看');

-- 角色：平台管理员（tenant_id=0）、租户管理员与只读（tenant_id=1）
INSERT INTO `t_role` (`id`, `tenant_id`, `code`, `name`, `remark`) VALUES
(1, 0, 'PLATFORM_ADMIN', '平台管理员', '可跨租户管理'),
(2, 1, 'TENANT_ADMIN',   '租户管理员', '仅管理本租户'),
(3, 1, 'VIEWER',         '只读用户',   '仅查看');

-- 角色-权限：平台管理员全权限；租户管理员除租户管理外；只读仅查看
INSERT INTO `t_role_permission` (`role_id`, `permission_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(2, 2), (2, 3), (2, 4), (2, 5),
(3, 4), (3, 5);

-- 用户（密码均为 123456）
INSERT INTO `t_user` (`id`, `tenant_id`, `username`, `password`, `nickname`, `status`) VALUES
(1, 0, 'superadmin',  '$2a$10$M7820CMuUEy1Fcgmh0Gr4.Mbo7zWwdPAEufXVjNXePyDF.ZmWC8lK', '平台超管',   1),
(2, 1, 'tenantadmin', '$2a$10$M7820CMuUEy1Fcgmh0Gr4.Mbo7zWwdPAEufXVjNXePyDF.ZmWC8lK', '租户管理员', 1),
(3, 1, 'viewer',      '$2a$10$M7820CMuUEy1Fcgmh0Gr4.Mbo7zWwdPAEufXVjNXePyDF.ZmWC8lK', '只读用户',   1),
(4, 1, 'disabled',    '$2a$10$M7820CMuUEy1Fcgmh0Gr4.Mbo7zWwdPAEufXVjNXePyDF.ZmWC8lK', '已禁用用户', 0);

-- 用户-角色
INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 3);
