import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/link',
    children: [
      {
        path: 'link',
        name: 'link',
        component: () => import('@/views/link/index.vue'),
        meta: { title: '短链管理' },
      },
      {
        path: 'group',
        name: 'group',
        component: () => import('@/views/group/index.vue'),
        meta: { title: '分组管理' },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板' },
      },
      {
        path: 'tenant',
        name: 'tenant',
        component: () => import('@/views/tenant/index.vue'),
        meta: { title: '租户管理', platformOnly: true },
      },
      {
        path: 'apikey',
        name: 'apikey',
        component: () => import('@/views/apikey/index.vue'),
        meta: { title: 'API Key', platformOnly: true },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/link' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    // 已登录还去登录页：直接回首页
    return auth.isLoggedIn ? { path: '/link' } : true
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!auth.user) {
    await auth.loadMe().catch((e) => console.error('load current user failed', e))
  }

  // 平台专属页面：非平台账号直接挡回短链管理
  if (to.meta.platformOnly && !auth.isPlatform) {
    return { path: '/link' }
  }

  return true
})

export default router
