import http, { type ApiResult } from './http'
import type { LoginPayload, LoginResult, TokenPair, UserInfo } from '@/types'

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const { data } = await http.post<ApiResult<LoginResult>>('/auth/login', payload)
  return data.data
}

export async function refresh(refreshToken: string): Promise<TokenPair> {
  const { data } = await http.post<ApiResult<TokenPair>>('/auth/refresh', { refreshToken })
  return data.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export async function getMe(): Promise<UserInfo> {
  const { data } = await http.get<ApiResult<UserInfo>>('/auth/me')
  return data.data
}
