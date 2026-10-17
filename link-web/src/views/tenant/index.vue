<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, RefreshCw } from 'lucide-vue-next'
import { createTenant, listTenants } from '@/api/tenant'
import type { TenantItem } from '@/types'

const loading = ref(false)
const rows = ref<TenantItem[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  quotaPerDay: 100000,
  adminUsername: '',
  adminPassword: '',
  adminNickname: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员用户名', trigger: 'blur' }],
  adminPassword: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6~64 之间', trigger: 'blur' },
  ],
}

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

function openCreate() {
  form.name = ''
  form.quotaPerDay = 100000
  form.adminUsername = ''
  form.adminPassword = ''
  form.adminNickname = ''
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function submit() {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return
    }
    saving.value = true
    createTenant({
      name: form.name.trim(),
      quotaPerDay: form.quotaPerDay,
      adminUsername: form.adminUsername.trim(),
      adminPassword: form.adminPassword,
      adminNickname: form.adminNickname.trim() || undefined,
    })
      .then((res) => {
        ElMessage.success(`租户「${res.tenantName}」已创建，管理员：${res.adminUsername}（请线下告知初始密码）`)
        dialogVisible.value = false
        load()
      })
      .catch((e) => console.error('create tenant failed', e))
      .finally(() => {
        saving.value = false
      })
  })
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between">
      <div>
        <h2 class="page-title">租户管理</h2>
        <p class="page-subtitle">仅平台管理员可见；开通租户会同时创建该租户的初始管理员账号</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button :icon="RefreshCw" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建租户</el-button>
      </div>
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

    <el-dialog v-model="dialogVisible" title="新建租户" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="租户名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如：某某科技" />
        </el-form-item>
        <el-form-item label="每日配额">
          <el-input-number v-model="form.quotaPerDay" :min="1" :step="10000" class="!w-full" />
        </el-form-item>

        <el-divider content-position="left">租户管理员账号</el-divider>

        <el-form-item label="用户名" prop="adminUsername">
          <el-input v-model="form.adminUsername" maxlength="64" placeholder="登录用户名（全局唯一）" />
        </el-form-item>
        <el-form-item label="初始密码" prop="adminPassword">
          <el-input v-model="form.adminPassword" type="password" show-password maxlength="64" placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.adminNickname" maxlength="64" placeholder="可空，默认同用户名" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
