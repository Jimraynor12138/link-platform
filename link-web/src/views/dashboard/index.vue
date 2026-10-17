<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { fetchChannelStats, fetchDailyStats } from '@/api/stats'
import ChartPanel from '@/components/ChartPanel.vue'
import type { ChannelStats, DailyStats } from '@/types'

const loading = ref(false)
const daily = ref<DailyStats[]>([])
const channels = ref<ChannelStats[]>([])
const range = ref<[string, string] | null>(null)

function fmt(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  return [fmt(start), fmt(end)]
}

function load() {
  const [from, to] = range.value ?? defaultRange()
  loading.value = true
  Promise.all([fetchDailyStats({ from, to }), fetchChannelStats({ from, to })])
    .then(([d, c]) => {
      daily.value = d
      channels.value = c
    })
    .catch((e) => console.error('load stats failed', e))
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  range.value = defaultRange()
  load()
})

const totalPv = computed(() => daily.value.reduce((sum, d) => sum + (d.pv ?? 0), 0))
const totalUv = computed(() => daily.value.reduce((sum, d) => sum + (d.uv ?? 0), 0))
const channelCount = computed(() => channels.value.length)

const lineOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['PV', 'UV'], right: 0 },
  grid: { left: 48, right: 24, top: 40, bottom: 32 },
  xAxis: {
    type: 'category',
    data: daily.value.map((d) => d.date),
    axisLine: { lineStyle: { color: '#E4E7ED' } },
    axisLabel: { color: '#909399' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F2F3F5' } },
    axisLabel: { color: '#909399' },
  },
  series: [
    {
      name: 'PV',
      type: 'line',
      smooth: true,
      data: daily.value.map((d) => d.pv ?? 0),
      itemStyle: { color: '#409EFF' },
      areaStyle: { color: 'rgba(64,158,255,0.12)' },
    },
    {
      name: 'UV',
      type: 'line',
      smooth: true,
      data: daily.value.map((d) => d.uv ?? 0),
      itemStyle: { color: '#67C23A' },
    },
  ],
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 24, top: 30, bottom: 32 },
  xAxis: {
    type: 'category',
    data: channels.value.map((c) => c.channel || '无渠道'),
    axisLabel: { color: '#909399' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F2F3F5' } },
    axisLabel: { color: '#909399' },
  },
  series: [
    {
      name: 'PV',
      type: 'bar',
      barMaxWidth: 36,
      data: channels.value.map((c) => c.pv ?? 0),
      itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] },
    },
  ],
}))
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-end justify-between flex-wrap gap-3">
      <div>
        <h2 class="page-title">数据看板</h2>
        <p class="page-subtitle">数据源为 t_link_stats 快照表；租户只看自己，平台管理员看全量</p>
      </div>
      <div class="flex items-center gap-2">
        <el-date-picker
          v-model="range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="!w-64"
          @change="load"
        />
        <el-button :icon="RefreshCw" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="grid grid-cols-3 gap-4">
      <div class="panel-card p-5">
        <p class="text-xs text-[#909399]">总 PV</p>
        <p class="text-[26px] font-semibold text-[#303133] mt-2">{{ totalPv.toLocaleString() }}</p>
      </div>
      <div class="panel-card p-5">
        <p class="text-xs text-[#909399]">总 UV（近似）</p>
        <p class="text-[26px] font-semibold text-[#303133] mt-2">{{ totalUv.toLocaleString() }}</p>
      </div>
      <div class="panel-card p-5">
        <p class="text-xs text-[#909399]">有数据的渠道数</p>
        <p class="text-[26px] font-semibold text-[#303133] mt-2">{{ channelCount }}</p>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div class="panel-card p-5">
        <h3 class="text-base font-semibold text-[#303133]">PV / UV 趋势</h3>
        <ChartPanel
          class="mt-3"
          :option="lineOption"
          :empty="daily.length === 0"
          empty-text="所选区间暂无统计数据"
        />
      </div>
      <div class="panel-card p-5">
        <h3 class="text-base font-semibold text-[#303133]">渠道效果（PV）</h3>
        <ChartPanel
          class="mt-3"
          :option="barOption"
          :empty="channels.length === 0"
          empty-text="所选区间暂无渠道数据"
        />
      </div>
    </div>
  </div>
</template>
