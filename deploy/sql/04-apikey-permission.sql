-- ============================================================
-- P6-4 修复：新增 apikey:manage 权限，允许租户管理员自助管理本租户的 API Key
--
-- 背景：P4 的 API Key 生成/列表只开放给 tenant:manage（平台管理员），
--      租户管理员无法自助申请/查看自己的 Key。
--
-- 幂等：t_permission 主键 + t_role_permission 唯一键 (role_id, permission_id)，
--      用 INSERT IGNORE 可重复执行。
-- ============================================================
-- 【P7】显式声明客户端字符集，避免容器内 CLI 按 latin1 导入导致中文双重编码
SET NAMES utf8mb4;

USE `link_platform`;

INSERT IGNORE INTO `t_permission` (`id`, `code`, `name`)
VALUES (6, 'apikey:manage', 'API Key 管理');

-- 平台管理员（role 1）与租户管理员（role 2）授予该权限；只读用户（role 3）不授予
INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`) VALUES
(1, 6),
(2, 6);
