import http, { type ApiResult } from './http'
import type { LinkItem, LinkPageQuery, LinkPageResult, LinkSavePayload } from '@/types'

export async function pageLinks(query: LinkPageQuery): Promise<LinkPageResult> {
  const { data } = await http.post<ApiResult<LinkPageResult>>('/api/v1/link/page', query)
  return data.data
}

export async function createLink(payload: LinkSavePayload): Promise<LinkItem> {
  const { data } = await http.post<ApiResult<LinkItem>>('/api/v1/link/create', payload)
  return data.data
}

export async function updateLink(payload: LinkSavePayload): Promise<void> {
  await http.put('/api/v1/link/update', payload)
}

export async function deleteLink(id: number): Promise<void> {
  await http.delete('/api/v1/link/del', { params: { id } })
}
