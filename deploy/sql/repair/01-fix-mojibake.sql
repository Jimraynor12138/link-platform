-- ============================================================
-- 修复：容器初始化时中文被双重编码（UTF-8 被按 Windows-1252 导入）
--
-- 现象：库里存的是「默认分组」的 UTF-8 字节被当成 cp1252 再转存的结果，
--       读出来是 é»˜è®¤åˆ†ç»„（HEX 以 C3A9 开头，长度 26 字节 / 12 字符）。
-- 根因：官方 mysql 镜像的 docker-entrypoint.sh 用 mysql CLI 执行
--       /docker-entrypoint-initdb.d/*.sql，客户端 --default-character-set=auto
--       跟随容器 locale；没设 LANG 时落到 latin1（MySQL 的 latin1 实际是 cp1252），
--       于是 0x98/0x86/0x84 被映射成 ˜/†/„（两个 3 字节字符）。
-- 根治：deploy/sql/0*.sql 已加 SET NAMES utf8mb4；compose 已加 LANG=C.UTF-8。
--
-- 本脚本：只修**已经写坏**的行，可重复执行（第二次匹配不到任何行）。
--
-- 【判据为什么不用 LENGTH=2*CHAR_LENGTH】
--   cp1252 的 0x80–0x9F 会被映射成 U+02xx/U+20xx（3 字节），
--   所以乱码串是"2 字节为主 + 少量 3 字节"，长度比不成立（26 ≠ 2×12）。
--   改用"首字符是 C2/C3 开头（即 é/æ/å 这类 2 字节拉丁字符）"识别：
--   正常中文的 UTF-8 首字节是 E4–E9，纯 ASCII 是 41–7A，都不会命中。
--
-- 执行方式（Windows cmd，`<` 是 cmd 的输入重定向，整条写一行）：
--   cd /d <repo>
--   docker exec -i link-mysql mysql -uroot -p<密码> < deploy\sql\repair\01-fix-mojibake.sql
-- ============================================================
SET NAMES utf8mb4;
USE `link_platform`;

UPDATE `t_group`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE HEX(`name`) REGEXP '^(C2|C3)';

UPDATE `t_tenant`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE HEX(`name`) REGEXP '^(C2|C3)';

UPDATE `t_role`
SET `name`   = CONVERT(BINARY(CONVERT(`name`   USING latin1)) USING utf8mb4),
    `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
WHERE HEX(`name`) REGEXP '^(C2|C3)';

UPDATE `t_user`
SET `nickname` = CONVERT(BINARY(CONVERT(`nickname` USING latin1)) USING utf8mb4)
WHERE HEX(`nickname`) REGEXP '^(C2|C3)';

-- 权限表通常是好的（实测 0 行命中），保留这条只为兜底
UPDATE `t_permission`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE HEX(`name`) REGEXP '^(C2|C3)';

-- 短链描述若含种子中文，同样处理
UPDATE `t_link`
SET `description` = CONVERT(BINARY(CONVERT(`description` USING latin1)) USING utf8mb4)
WHERE `description` IS NOT NULL
  AND HEX(`description`) REGEXP '^(C2|C3)';

-- 校验：修复后 HEX 应为中文 UTF-8（默认分组 = E9BB98E8AEA4E58886E7BB84）
SELECT 'group' AS tbl, `name`, HEX(`name`) AS hex_name FROM `t_group` WHERE `gid` = 'default'
UNION ALL SELECT 'tenant', `name`, HEX(`name`) FROM `t_tenant`
UNION ALL SELECT 'role',   `name`, HEX(`name`) FROM `t_role`
UNION ALL SELECT 'perm',   `name`, HEX(`name`) FROM `t_permission`
UNION ALL SELECT 'user',   `nickname`, HEX(`nickname`) FROM `t_user`;
