-- ============================================================
-- 演示数据：回填过去 14 天点击统计（**不含今天**，由 scripts/seed-demo.mjs 生成）
--
-- ⚠️ 这是**回填的模拟历史**，不是真实点击。
--    今天这一行刻意留空，交给真实点击（跳转 -> MQ -> 消费 -> 每 2s flush）去累积，
--    否则 upsertStats 的"只增不减"规则会让真实点击永远显示不出来。
--
-- 数据特征：按渠道给不同量级 + 越接近今天越高 + 由 CRC32 产生确定性抖动
-- 可重复执行：先按短码删掉这些链的统计，再整批插入
-- ============================================================
SET NAMES utf8mb4;
USE `link_platform`;

DELETE FROM t_link_stats
WHERE link_id IN (SELECT id FROM t_link WHERE code IN ('2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd'));

INSERT INTO t_link_stats
    (link_id, tenant_id, code, channel, date, pv, uv, ip_cnt, default_cnt, referer_stats, device_stats)
SELECT
    x.id,
    x.tenant_id,
    x.code,
    x.channel,
    DATE_SUB(CURDATE(), INTERVAL x.n DAY),
    x.pv,
    GREATEST(1, FLOOR(x.pv * (0.35 + MOD(CRC32(CONCAT(x.code, 'uv', x.n)), 30) / 100))),
    GREATEST(1, FLOOR(x.pv * 0.85)),
    FLOOR(x.pv * 0.30),
    CONCAT('{"direct":', FLOOR(x.pv * 0.30), '}'),
    CONCAT('{"mobile":', FLOOR(x.pv * 0.72), ',"pc":', x.pv - FLOOR(x.pv * 0.72), '}')
FROM (
    SELECT
        l.id,
        l.tenant_id,
        l.code,
        l.channel,
        d.n,
        ROUND(
            (CASE l.channel
                WHEN 'douyin'      THEN 220
                WHEN 'wechat'      THEN 160
                WHEN 'weibo'       THEN 110
                WHEN 'xiaohongshu' THEN 140
                WHEN 'email'       THEN 80
                WHEN 'offline'     THEN 60
                WHEN 'partner'     THEN 50
                ELSE 40
            END) * (0.70 + MOD(CRC32(CONCAT(l.code, '-', d.n)), 60) / 100)
        ) + (14 - d.n) * 6 AS pv
    FROM t_link l
    JOIN (SELECT 1 AS n UNION ALL SELECT 2 AS n UNION ALL SELECT 3 AS n UNION ALL SELECT 4 AS n UNION ALL SELECT 5 AS n UNION ALL SELECT 6 AS n UNION ALL SELECT 7 AS n UNION ALL SELECT 8 AS n UNION ALL SELECT 9 AS n UNION ALL SELECT 10 AS n UNION ALL SELECT 11 AS n UNION ALL SELECT 12 AS n UNION ALL SELECT 13 AS n UNION ALL SELECT 14 AS n) d
    WHERE l.code IN ('2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd')
      AND l.del_flag = 0
) x;
