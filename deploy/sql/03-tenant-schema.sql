-- ============================================================
-- 多租户 + 渠道迁移脚本（V4 / P4）
-- 库：link_platform（库为空、无历史数据，无需回填）
-- 执行方式：mysql -uroot -p < deploy/sql/03-tenant-schema.sql
-- 说明：
--   1) 短码保持全局唯一：跳转 /{code} 靠短码命中，访客免租户；
--      tenant_id 隔离只作用于创建/查询/统计等管理链路。
--   2) 本脚本是一次性迁移（ALTER），在空库上执行一次即可。
-- ============================================================

-- 【P7】显式声明客户端字符集，避免容器内 CLI 按 latin1 导入导致中文双重编码
SET NAMES utf8mb4;

USE `link_platform`;

-- ------------------------------------------------------------
-- 1. t_link：加 tenant_id + channel
-- ------------------------------------------------------------
ALTER TABLE `t_link`
    ADD COLUMN `tenant_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '所属租户：0=平台级 >0=租户级' AFTER `id`,
    ADD COLUMN `channel`   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '渠道标识（可选，空=无渠道，用于点击归因）' AFTER `gid`;

-- 索引：管理链路按租户过滤（旧 idx_gid_create 被租户感知索引取代）
ALTER TABLE `t_link`
    DROP KEY `idx_gid_create`,
    ADD KEY `idx_tenant_create` (`tenant_id`, `del_flag`, `create_time`),
    ADD KEY `idx_tenant_gid` (`tenant_id`, `gid`, `del_flag`);

-- ------------------------------------------------------------
-- 2. t_link_stats：加 tenant_id + channel，唯一键改为含 channel
--    channel 用 NOT NULL DEFAULT ''：MySQL 唯一索引把 NULL 视为互不相同，
--    若允许 NULL，"无渠道"统计会出多行、upsert 失效。
-- ------------------------------------------------------------
ALTER TABLE `t_link_stats`
    ADD COLUMN `tenant_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '所属租户' AFTER `link_id`,
    ADD COLUMN `channel`   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '渠道标识' AFTER `code`;

ALTER TABLE `t_link_stats`
    DROP KEY `uk_link_date`,
    ADD UNIQUE KEY `uk_link_date_channel` (`link_id`, `date`, `channel`),
    ADD KEY `idx_tenant_date` (`tenant_id`, `date`);

-- ------------------------------------------------------------
-- 3. t_group：加 tenant_id，分组按租户隔离
-- ------------------------------------------------------------
ALTER TABLE `t_group`
    ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户：0=平台级 >0=租户级' AFTER `id`;

ALTER TABLE `t_group`
    DROP KEY `uk_gid`,
    ADD UNIQUE KEY `uk_tenant_gid` (`tenant_id`, `gid`);
