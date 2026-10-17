import http, { type ApiResult } from './http'
import type { ChannelStats, DailyStats } from '@/types'

export interface StatsRange {
  from?: string
  to?: string
}

/** 看板：按日汇总 PV/UV（数据源 t_link_stats，受多租户隔离） */
export async function fetchDailyStats(range: StatsRange): Promise<DailyStats[]> {
  const { data } = await http.get<ApiResult<DailyStats[]>>('/api/v1/stats/overview', { params: range })
  return data.data ?? []
}

/** 看板：按渠道汇总 PV/UV */
export async function fetchChannelStats(range: StatsRange): Promise<ChannelStats[]> {
  const { data } = await http.get<ApiResult<ChannelStats[]>>('/api/v1/stats/channels', { params: range })
  return data.data ?? []
}
