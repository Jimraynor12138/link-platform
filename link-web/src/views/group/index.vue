<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, RefreshCw, Trash2 } from 'lucide-vue-next'
import { createGroup, deleteGroup, listGroups } from '@/api/group'
import type { GroupItem } from '@/types'

const loading = ref(false)
const rows = ref<GroupItem[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', sortOrder: 0 })

function load() {
  loading.value = true
  listGroups()
    .then((list) => {
      rows.value = list
    })
    .catch((e) => console.error('load groups failed', e))
    .finally(() => {
      loading.value = false
    })
}

onMounted(load)

function openCreate() {
  form.name = ''
  form.sortOrder = rows.value.length
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入分组名称')
    return
  }
  saving.value = true
  createGroup({ name: form.name.trim(), sortOrder: form.sortOrder })
    .then(() => {
      ElMessage.success('分组已创建')
      dialogVisible.value = false
      load()
    })
    .catch((e) => console.error('create group failed', e))
    .finally(() => {
      saving.value = false
    })
}

function remove(row: GroupItem) {
  ElMessageBox.confirm(
    `确定删除分组「${row.name}」吗？该分组下的短链不受影响（仍可在"全部分组"里看到）。`,
    '删除确认',
    { type: 'warning' },
  )
    .then(() => deleteGroup(row.gid))
    .then(() => {
      ElMessage.success('已删除')
      load()
    })
    .catch((e) => {
      if (e !== 'cancel') {
        console.error('delete group failed', e)
      }
    })
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between">
      <div>
        <h2 class="page-title">分组管理</h2>
        <p class="page-subtitle">用分组归类短链；删除分组不会删除其下短链</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button :icon="RefreshCw" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建分组</el-button>
      </div>
    </div>

    <div class="panel-card overflow-hidden">
      <el-table :data="rows" v-loading="loading" stripe class="w-full">
        <el-table-column prop="name" label="分组名称" min-width="180" />
        <el-table-column label="分组标识" width="180">
          <template #default="{ row }">
            <span class="code-text">{{ row.gid }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Trash2" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="新建分组" width="420px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="如：春季活动" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
