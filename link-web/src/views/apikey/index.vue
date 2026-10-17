<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Copy, KeyRound, RefreshCw } from 'lucide-vue-next'
import { createApiKey, listApiKeys, listTenants } from '@/api/tenant'
import { useAuthStore } from '@/stores/auth'
import type { ApiKeyCreated, ApiKeyItem, TenantItem } from '@/types'

const auth = useAuthStore()
const isPlatform = computed(() => auth.isPlatform)

const loading = ref(false)
const keys = ref<ApiKeyItem[]>([])
const tenants = ref<TenantItem[]>([])
const selectedTenant = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ tenantId: 0, quota: 100000, expireAt: '' })

const secretVisible = ref(false)
const created = ref<ApiKeyCreated | null>(null)

function loadTenants() {
  listTenants()
    .then((list) => {
      tenants.value = list
      if (!form.tenantId && list.length > 0) {
        form.tenantId = list[0].id
      }
    })
    .catch((e) => console.error('load tenants failed', e))
}

function loadKeys() {
  loading.value = true
  listApiKeys(selectedTenant.value)
    .then((list) => {
      keys.value = list
    })
    .catch((e) => console.error('load api keys failed', e))
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  if (isPlatform.value) {
    loadTenants()
  }
  loadKeys()
})

function openCreate() {
  form.tenantId = isPlatform.value
    ? (selectedTenant.value ?? tenants.value[0]?.id ?? 0)
    : (auth.user?.tenantId ?? 0)
  form.quota = 100000
  form.expireAt = ''
  dialogVisible.value = true
}

function submit() {
  if (isPlatform.value && !form.tenantId) {
    ElMessage.warning('请选择租户')
    return
  }
  saving.value = true
  createApiKey({
    tenantId: form.tenantId,
    quota: form.quota,
    expireAt: form.expireAt || null,
  })
    .then((result) => {
      dialogVisible.value = false
      created.value = result
      secretVisible.value = true
      loadKeys()
    })
    .catch((e) => console.error('create api key failed', e))
    .finally(() => {
      saving.value = false
    })
}

function copy(text: string) {
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动选择'))
}

function expireText(value: string | null) {
  return value ? value.replace('T', ' ') : '永久'
}

const tenantOptions = computed(() => tenants.value)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between flex-wrap gap-3">
      <div>
        <h2 class="page-title">API Key</h2>
        <p class="page-subtitle">开放接口凭据：secret 仅在生成时展示一次，列表不再返回</p>
      </div>
      <div class="flex items-center gap-2">
        <el-select
          v-if="isPlatform"
          v-model="selectedTenant"
          placeholder="全部租户"
          clearable
          class="!w-44"
          @change="loadKeys"
        >
          <el-option v-for="t in tenantOptions" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-button :icon="RefreshCw" @click="loadKeys">刷新</el-button>
        <el-button type="primary" :icon="KeyRound" @click="openCreate">生成 Key</el-button>
      </div>
    </div>

    <div class="panel-card overflow-hidden">
      <el-table :data="keys" v-loading="loading" stripe class="w-full">
        <el-table-column label="Access Key" min-width="260">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span class="code-text">{{ row.accessKey }}</span>
              <button class="text-[#909399] hover:text-brand" title="复制" @click="copy(row.accessKey)">
                <Copy :size="14" />
              </button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="配额" width="140">
          <template #default="{ row }">{{ (row.quota ?? 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="180">
          <template #default="{ row }">{{ expireText(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && keys.length === 0" description="暂无 API Key，点击右上角生成" />
    </div>

    <el-dialog v-model="dialogVisible" title="生成 API Key" width="460px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item v-if="isPlatform" label="租户" required>
          <el-select v-model="form.tenantId" class="w-full" placeholder="选择租户">
            <el-option v-for="t in tenants" :key="t.id" :label="`${t.name}（#${t.id}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配额">
          <el-input-number v-model="form.quota" :min="1" :step="10000" class="!w-full" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            placeholder="留空表示永久"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="w-full"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretVisible" title="请立即保存 Secret" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="secret 仅此一次可见，关闭后无法再次查看" />
      <div class="mt-4 space-y-3">
        <div>
          <p class="text-xs text-[#909399] mb-1">Access Key</p>
          <div class="flex items-center gap-2">
            <span class="code-text break-all">{{ created?.accessKey }}</span>
            <button class="text-[#909399] hover:text-brand shrink-0" @click="copy(created?.accessKey ?? '')">
              <Copy :size="14" />
            </button>
          </div>
        </div>
        <div>
          <p class="text-xs text-[#909399] mb-1">Secret</p>
          <div class="flex items-center gap-2">
            <span class="code-text break-all">{{ created?.secret }}</span>
            <button class="text-[#909399] hover:text-brand shrink-0" @click="copy(created?.secret ?? '')">
              <Copy :size="14" />
            </button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
