package com.aaf.link.core.dto;

import lombok.Data;

/**
 * 看板：按日汇总的 PV/UV（P6）。
 *
 * <p>数据源是 MySQL 的 {@code t_link_stats} 快照表（不是 Redis 实时口径）——
 * 看板要看"跨天趋势"，实时 key 有 TTL、且单码单日，做不了趋势。
 */
@Data
public class DailyStatsVO {

    /** 统计日期 yyyy-MM-dd（字符串承接，避免前端时区问题） */
    private String date;

    /** 当日总 PV */
    private Long pv;

    /** 当日总 UV（各渠道 UV 之和，跨渠道可能重复计数，属近似值） */
    private Long uv;
}
