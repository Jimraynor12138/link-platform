package com.aaf.link.core.dto;

import lombok.Data;

/**
 * 看板：按渠道汇总的 PV/UV（P6 渠道效果报表）。
 *
 * <p>渠道是短链属性（创建时固定），快照落库时从 {@code t_link} 冗余写入 {@code t_link_stats.channel}，
 * 因此可以直接 GROUP BY channel 出渠道效果。
 */
@Data
public class ChannelStatsVO {

    /** 渠道标识；空串表示"无渠道"，由前端展示为"无渠道" */
    private String channel;

    /** 该渠道总 PV */
    private Long pv;

    /** 该渠道总 UV（各短链 UV 之和，属近似值） */
    private Long uv;
}
