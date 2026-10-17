import http, { type ApiResult } from './http'
import type { GroupItem } from '@/types'

export async function listGroups(): Promise<GroupItem[]> {
  const { data } = await http.get<ApiResult<GroupItem[]>>('/api/v1/group/list')
  return data.data ?? []
}

/** 创建分组：gid 由后端生成并返回 */
export async function createGroup(payload: { name: string; sortOrder?: number }): Promise<string> {
  const { data } = await http.post<ApiResult<string>>('/api/v1/group/create', payload)
  return data.data
}

export async function deleteGroup(gid: string): Promise<void> {
  await http.delete('/api/v1/group/del', { params: { gid } })
}
