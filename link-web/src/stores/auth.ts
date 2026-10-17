import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import { tokenStore } from '@/api/http'
import type { UserInfo } from '@/types'

/**
 * 登录态：token 持久化到 localStorage，用户信息（含权限）按需拉取。
 *
 * 视图区分规则：tenantId === 0 为平台级（可看租户/API Key 菜单）。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const accessToken = ref(tokenStore.access())

  const isLoggedIn = computed(() => !!accessToken.value)
  const isPlatform = computed(() => user.value?.tenantId === 0)
  const permissions = computed(() => user.value?.permissions ?? [])
  const displayName = computed(() => user.value?.nickname || user.value?.username || '未登录')

  function setTokens(access: string, refresh?: string) {
    accessToken.value = access
    tokenStore.save(access, refresh)
  }

  async function login(username: string, password: string) {
    const result = await loginApi({ username, password })
    setTokens(result.accessToken, result.refreshToken)
    await loadMe()
  }

  async function loadMe() {
    if (!accessToken.value) {
      return
    }
    user.value = await getMe()
  }

  async function logout() {
    await logoutApi().catch((e) => console.error('logout api failed', e))
    accessToken.value = ''
    user.value = null
    tokenStore.clear()
  }

  return {
    user,
    accessToken,
    isLoggedIn,
    isPlatform,
    permissions,
    displayName,
    login,
    loadMe,
    logout,
    setTokens,
  }
})
