import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应体（注意：code 是字符串，"0" 才代表成功） */
export interface ApiResult<T = unknown> {
  code: string
  message: string
  data: T
}

type RetryableConfig = InternalAxiosRequestConfig & { __retried?: boolean }

const ACCESS_KEY = 'link_web_access_token'
const REFRESH_KEY = 'link_web_refresh_token'

/** token 持久化：刷新页面后仍保持登录态 */
export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY) ?? '',
  refresh: () => localStorage.getItem(REFRESH_KEY) ?? '',
  save(access: string, refresh?: string) {
    localStorage.setItem(ACCESS_KEY, access)
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

const http = axios.create({ baseURL: '/', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = tokenStore.access()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 刷新单飞：并发 401 共享同一次刷新，避免刷新风暴 */
let refreshing: Promise<string> | null = null

function refreshAccessToken(): Promise<string> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = tokenStore.refresh()
      if (!refreshToken) {
        throw new Error('missing refresh token')
      }
      const resp = await axios.post<ApiResult<{ accessToken: string; refreshToken: string }>>(
        '/auth/refresh',
        { refreshToken },
      )
      const body = resp.data
      if (body.code !== '0' || !body.data?.accessToken) {
        throw new Error(body.message || 'refresh failed')
      }
      tokenStore.save(body.data.accessToken, body.data.refreshToken)
      return body.data.accessToken
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    // 业务失败走 HTTP 200 + 非 "0" 的 code
    if (body && typeof body.code === 'string' && body.code !== '0') {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || 'request failed'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const config = error?.config as RetryableConfig | undefined
    const isAuthApi = String(config?.url ?? '').includes('/auth/login')

    // access token 失效：单飞刷新后重放原请求
    if (status === 401 && config && !config.__retried && !isAuthApi) {
      config.__retried = true
      return refreshAccessToken()
        .then((token) => {
          config.headers.Authorization = `Bearer ${token}`
          return http(config)
        })
        .catch((e) => {
          console.error('token refresh failed', e)
          tokenStore.clear()
          ElMessage.error('登录已过期，请重新登录')
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login'
          }
          return Promise.reject(e)
        })
    }

    if (status === 403) {
      ElMessage.error('无权限访问该资源')
    } else if (status === 429) {
      ElMessage.error('请求过于频繁，请稍后再试')
    } else {
      ElMessage.error(error?.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default http
