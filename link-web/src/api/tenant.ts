import http, { type ApiResult } from './http'
import type { ApiKeyCreated, ApiKeyItem, TenantItem } from '@/types'

/** 平台侧：租户列表 */
export async function listTenants(): Promise<TenantItem[]> {
  const { data } = await http.get<ApiResult<TenantItem[]>>('/auth/tenants')
  return data.data ?? []
}

/** 平台侧：按租户查 API Key 列表（不含 secret） */
export async function listApiKeys(tenantId?: number): Promise<ApiKeyItem[]> {
  const { data } = await http.get<ApiResult<ApiKeyItem[]>>('/auth/api-keys', {
    params: tenantId ? { tenantId } : {},
  })
  return data.data ?? []
}

/** 平台侧：生成 API Key（secret 仅本次返回可见） */
export async function createApiKey(payload: {
  tenantId: number
  quota?: number
  expireAt?: string | null
}): Promise<ApiKeyCreated> {
  const { data } = await http.post<ApiResult<ApiKeyCreated>>('/auth/api-keys', payload)
  return data.data
}

export interface TenantCreatePayload {
  name: string
  quotaPerDay?: number
  adminUsername: string
  adminPassword: string
  adminNickname?: string
}

export interface TenantCreated {
  tenantId: number
  tenantName: string
  adminUsername: string
}

/** 平台侧：开通新租户（同时创建租户管理员账号） */
export async function createTenant(payload: TenantCreatePayload): Promise<TenantCreated> {
  const { data } = await http.post<ApiResult<TenantCreated>>('/auth/tenants', payload)
  return data.data
}
