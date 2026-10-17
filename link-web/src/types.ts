/** 与后端契约对应的前端类型（P6） */

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  id: number
  username: string
  tenantId: number
  accessToken: string
  refreshToken: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  tenantId: number
  roles: string[]
  permissions: string[]
}

export interface LinkItem {
  id: number
  code: string
  fullShortUrl: string
  originalUrl: string
  gid: string
  channel: string
  enableStatus: number
  validType: number
  validDate: string | null
  description: string
  createTime: string
}

export interface LinkPageQuery {
  current: number
  size: number
  gid?: string
}

export interface LinkPageResult {
  records: LinkItem[]
  total: number
  current: number
  size: number
}

export interface LinkSavePayload {
  id?: number
  originalUrl: string
  gid?: string
  channel?: string
  validType: number
  validDate?: string | null
  description?: string
  enableStatus?: number
}

export interface GroupItem {
  id: number
  gid: string
  name: string
  sortOrder: number
  createTime: string
}

export interface DailyStats {
  date: string
  pv: number
  uv: number
}

export interface ChannelStats {
  channel: string
  pv: number
  uv: number
}

export interface TenantItem {
  id: number
  name: string
  status: number
  quotaPerDay: number
}

export interface ApiKeyItem {
  accessKey: string
  quota: number
  expireAt: string | null
  status: number
  createTime: string
}

export interface ApiKeyCreated {
  accessKey: string
  secret: string
  notice: string
}
