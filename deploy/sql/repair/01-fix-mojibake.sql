-- ============================================================
-- 修复：Docker 初始化时中文被双重编码（UTF-8 被按 Latin-1 导入）
--
-- 现象：库里存的是「默认分组」的 UTF-8 字节被当成 latin1 再转存的结果，
--       读出来是 é»˜è®¤åˆ†ç»„（HEX 以 C3A9 开头）。
-- 根因：官方 mysql 镜像的 docker-entrypoint.sh 用 mysql CLI 执行
--       /docker-entrypoint-initdb.d/*.sql，客户端 --default-character-set=auto
--       跟随容器 locale；未设 LANG 时落到 latin1。
-- 根治：deploy/sql/0*.sql 已加 SET NAMES utf8mb4；compose 已加 LANG=C.UTF-8。
-- 本脚本：只修复**已经写坏**的数据，可重复执行（第二次匹配不到任何行）。
--
-- 执行方式（Windows cmd，注意 < 是 cmd 的重定向，不是 bash 续行）：
--   cd /d <repo>
--   docker exec -i link-mysql mysql -uroot -plink123456 < deploy\sql\repair\01-fix-mojibake.sql
-- ============================================================
SET NAMES utf8mb4;
USE `link_platform`;

-- 判据说明：
--   乱码行的每个字符都落在 U+0080–U+00FF（é/»/˜…），utf8mb4 下占 2 字节；
--   正常中文占 3 字节，纯 ASCII 占 1 字节。
--   因此 LENGTH = 2 × CHAR_LENGTH 是"整串都被拉丁化过"的可靠特征。
--   再叠加 id 范围（只碰种子数据），避免误伤后续通过 UI 正常写入的行。

UPDATE `t_group`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE `gid` = 'default'
  AND LENGTH(`name`) = 2 * CHAR_LENGTH(`name`);

UPDATE `t_tenant`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE `id` <= 3
  AND LENGTH(`name`) = 2 * CHAR_LENGTH(`name`);

UPDATE `t_role`
SET `name`   = CONVERT(BINARY(CONVERT(`name`   USING latin1)) USING utf8mb4),
    `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
WHERE `id` <= 3
  AND LENGTH(`name`) = 2 * CHAR_LENGTH(`name`);

UPDATE `t_user`
SET `nickname` = CONVERT(BINARY(CONVERT(`nickname` USING latin1)) USING utf8mb4)
WHERE `id` <= 4
  AND LENGTH(`nickname`) = 2 * CHAR_LENGTH(`nickname`);

-- t_permission 里有混合串（"API Key 管理" = ASCII + 中文），2 倍判据不成立，
-- 改用"HEX 里出现 C2/C3 开头的双字节序列"来识别（正常中文的字节是 E4~E9 开头，
-- 续字节都在 80~BF，不会出现 C2/C3 作为序列首字节）。
UPDATE `t_permission`
SET `name` = CONVERT(BINARY(CONVERT(`name` USING latin1)) USING utf8mb4)
WHERE `id` <= 6
  AND HEX(`name`) REGEXP 'C[23][0-9A-F]{2}';

-- 校验：修复后 HEX 应以 E9BB98（默认）、E6BC94（演示）等中文 UTF-8 开头
SELECT 'group' AS tbl, `name`, HEX(`name`) AS hex_name FROM `t_group` WHERE `gid` = 'default'
UNION ALL SELECT 'tenant', `name`, HEX(`name`) FROM `t_tenant`
UNION ALL SELECT 'role',   `name`, HEX(`name`) FROM `t_role`
UNION ALL SELECT 'perm',   `name`, HEX(`name`) FROM `t_permission`
UNION ALL SELECT 'user',   `nickname`, HEX(`nickname`) FROM `t_user`;
