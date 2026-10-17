<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  Building2,
  FolderTree,
  KeyRound,
  Link2,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ (e: 'toggle'): void }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 菜单：租户管理仅平台可见；API Key 平台或持 apikey:manage 的租户管理员可见
 */
const menus = computed(() => {
  const canManageApiKey = auth.isPlatform || auth.permissions.includes('apikey:manage')
  const items = [
    { path: '/link', label: '短链管理', icon: Link2, show: true },
    { path: '/group', label: '分组管理', icon: FolderTree, show: true },
    { path: '/dashboard', label: '数据看板', icon: BarChart3, show: true },
    { path: '/tenant', label: '租户管理', icon: Building2, show: auth.isPlatform },
    { path: '/apikey', label: 'API Key', icon: KeyRound, show: canManageApiKey },
  ]
  return items.filter((item) => item.show)
})

function go(path: string) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<template>
  <aside
    class="bg-sidebar text-white flex flex-col transition-all duration-200 shrink-0"
    :class="collapsed ? 'w-16' : 'w-60'"
  >
    <div class="h-14 flex items-center gap-2.5 px-4 border-b border-white/10">
      <div class="w-8 h-8 rounded-md bg-brand flex items-center justify-center shrink-0">
        <Link2 :size="18" />
      </div>
      <span v-if="!collapsed" class="font-semibold tracking-wide whitespace-nowrap">Link 短链平台</span>
    </div>

    <nav class="flex-1 py-3 thin-scroll overflow-y-auto">
      <button
        v-for="item in menus"
        :key="item.path"
        class="w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors relative"
        :class="route.path === item.path
          ? 'bg-white/10 text-white'
          : 'text-white/70 hover:text-white hover:bg-white/5'"
        @click="go(item.path)"
      >
        <span v-if="route.path === item.path" class="absolute left-0 top-0 h-full w-[3px] bg-brand" />
        <component :is="item.icon" :size="18" class="shrink-0" />
        <span v-if="!collapsed" class="whitespace-nowrap">{{ item.label }}</span>
      </button>
    </nav>

    <button
      class="h-12 flex items-center justify-center border-t border-white/10 text-white/60 hover:text-white transition-colors"
      @click="emit('toggle')"
    >
      <PanelLeftOpen v-if="collapsed" :size="18" />
      <PanelLeftClose v-else :size="18" />
    </button>
  </aside>
</template>
