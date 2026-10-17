<script setup lang="ts">
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'

// 按需注册，减小打包体积
use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

withDefaults(
  defineProps<{
    option: EChartsOption
    height?: string
    empty?: boolean
    emptyText?: string
  }>(),
  {
    height: '320px',
    empty: false,
    emptyText: '近 7 天暂无数据',
  },
)
</script>

<template>
  <!-- 容器必须有显式高度，否则 ECharts 不渲染（常见白屏） -->
  <div class="w-full" :style="{ height }">
    <el-empty v-if="empty" :description="emptyText" class="h-full flex flex-col justify-center" />
    <VChart v-else :option="option" autoresize class="w-full h-full" />
  </div>
</template>
