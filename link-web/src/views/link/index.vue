<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Copy, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next'
import { deleteLink, pageLinks, updateLink } from '@/api/link'
import { listGroups } from '@/api/group'
import LinkFormDialog from './LinkFormDialog.vue'
import type { GroupItem, LinkItem } from '@/types'

const loading = ref(false)
const rows = ref<LinkItem[]>([])
const total = ref(0)
const groups = ref<GroupItem[]>([])

const query = reactive({ current: 1, size: 10, gid: '' })
const dialogVisible = ref(false)
const editing = ref<LinkItem | null>(null)

function loadGroups() {
  listGroups()
    .then((list) => {
      groups.value = list
    })
    .catch((e) => console.error('load groups failed', e))
}

function load() {
  loading.value = true
  pageLinks({ current: query.current, size: query.size, gid: query.gid || undefined })
    .then((page) => {
      rows.value = page.records ?? []
      total.value = page.total ?? 0
    })
    .catch((e) => console.error('load links failed', e))
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  loadGroups()
  load()
})

function onFilterChange() {
  query.current = 1
  load()
}

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(row: LinkItem) {
  editing.value = row
  dialogVisible.value = true
}

function toggleStatus(row: LinkItem) {
  const next = row.enableStatus === 0 ? 1 : 0
  updateLink({ id: row.id, originalUrl: row.originalUrl, enableStatus: next })
    .then(() => {
      row.enableStatus = next
      ElMessage.success(next === 0 ? '已启用' : '已停用')
    })
    .catch((e) => console.error('toggle status failed', e))
}

function remove(row: LinkItem) {
  ElMessageBox.confirm(`确定删除短链 ${row.code} 吗？删除后跳转将失效。`, '删除确认', { type: 'warning' })
    .then(() => deleteLink(row.id))
    .then(() => {
      ElMessage.success('已删除')
      load()
    })
    .catch((e) => {
      if (e !== 'cancel') {
        console.error('delete link failed', e)
      }
    })
}

function copy(text: string) {
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动选择'))
}

function groupName(gid: string) {
  return groups.value.find((g) => g.gid === gid)?.name ?? (gid || '默认分组')
}

function validText(row: LinkItem) {
  return row.validType === 2 && row.validDate ? row.validDate.replace('T', ' ') : '永久'
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between flex-wrap gap-3">
      <div>
        <h2 class="page-title">短链管理</h2>
        <p class="page-subtitle">创建、启停与查看短链；渠道字段用于点击归因与渠道效果报表</p>
      </div>
      <div class="flex items-center gap-2">
        <el-select v-model="query.gid" placeholder="全部分组" clearable class="!w-40" @change="onFilterChange">
          <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
        </el-select>
        <el-button :icon="RefreshCw" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建短链</el-button>
      </div>
    </div>

    <div class="panel-card overflow-hidden">
      <el-table :data="rows" v-loading="loading" stripe class="w-full">
        <el-table-column label="短码" width="130">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span class="code-text">{{ row.code }}</span>
              <button class="text-[#909399] hover:text-brand transition-colors" title="复制短链" @click="copy(row.fullShortUrl)">
                <Copy :size="14" />
              </button>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="原始链接" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <a :href="row.originalUrl" target="_blank" class="text-brand hover:underline">{{ row.originalUrl }}</a>
          </template>
        </el-table-column>

        <el-table-column label="分组" width="120">
          <template #default="{ row }">{{ groupName(row.gid) }}</template>
        </el-table-column>

        <el-table-column label="渠道" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.channel" size="small" effect="plain">{{ row.channel }}</el-tag>
            <span v-else class="text-[#C0C4CC]">无渠道</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 0 ? 'success' : 'info'" size="small">
              {{ row.enableStatus === 0 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="有效期" width="160">
          <template #default="{ row }">{{ validText(row) }}</template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="flex items-center gap-1">
              <el-button link type="primary" :icon="Pencil" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="row.enableStatus === 0 ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ row.enableStatus === 0 ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" :icon="Trash2" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end p-3 border-t border-[#EBEEF5]">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="onFilterChange"
        />
      </div>
    </div>

    <LinkFormDialog v-model="dialogVisible" :link="editing" :groups="groups" @saved="load" />
  </div>
</template>
