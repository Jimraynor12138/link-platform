<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { listTenants } from '@/api/tenant'
import type { TenantItem } from '@/types'

const loading = ref(false)
const rows = ref<TenantItem[]>([])

function load() {
  loading.value = true
  listTenants()
    .then((list) => {
      rows.value = list
    })
    .catch((e) => console.error('load tenants failed', e))
    .finally(() => {
      loading.value = false
    })
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between">
      <div>
        <h2 class="page-title">租户管理</h2>
        <p class="page-subtitle">仅平台管理员可见；租户数据在管理链路按 tenant_id 自动隔离</p>
      </div>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </div>

    <div class="panel-card overflow-hidden">
      <el-table :data="rows" v-loading="loading" stripe class="w-full">
        <el-table-column prop="id" label="租户 ID" width="110" />
        <el-table-column prop="name" label="租户名称" min-width="200" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="每日配额" width="160">
          <template #default="{ row }">{{ (row.quotaPerDay ?? 0).toLocaleString() }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="暂无租户" />
    </div>
  </div>
</template>
