<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ChevronRight, LogOut, UserCircle2 } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const title = computed(() => (route.meta.title as string) || '工作台')
const tenantLabel = computed(() =>
  auth.user?.tenantId === 0 ? '平台管理员' : `租户 #${auth.user?.tenantId ?? '-'}`,
)

function onLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    .then(async () => {
      await auth.logout()
      router.replace('/login')
    })
    .catch(() => {
      // 用户取消，无需处理
    })
}
</script>

<template>
  <header class="h-14 bg-white border-b border-[#EBEEF5] flex items-center justify-between px-6 shrink-0">
    <div class="flex items-center gap-2 text-[13px] text-[#909399]">
      <span>控制台</span>
      <ChevronRight :size="14" />
      <span class="text-[#303133] font-medium">{{ title }}</span>
    </div>

    <div class="flex items-center gap-4">
      <span class="text-xs px-2 py-1 rounded bg-brand-light text-brand-dark">{{ tenantLabel }}</span>

      <div class="flex items-center gap-2 text-sm text-[#303133]">
        <UserCircle2 :size="20" class="text-[#909399]" />
        <span class="font-medium">{{ auth.displayName }}</span>
      </div>

      <button
        class="flex items-center gap-1.5 text-sm text-[#606266] hover:text-brand transition-colors px-2 py-1 rounded hover:bg-brand-light"
        @click="onLogout"
      >
        <LogOut :size="16" />
        退出
      </button>
    </div>
  </header>
</template>
