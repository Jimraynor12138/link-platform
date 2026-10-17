#!/usr/bin/env node
/**
 * 极简压测脚本（零依赖，Node 18+ 全局 fetch）。
 *
 * 用法：
 *   node bench/loadtest.mjs --url http://localhost:8080/abc123 --concurrency 50 --duration 30 --label "经网关"
 *
 * 说明：
 * - `redirect: 'manual'` 很关键：跳转压测不能跟随 302 到外网，否则测的是 example.com 的延迟
 * - 统计口径是「客户端观测」，含网络与序列化开销（这正是我们要对比的"多一跳"）
 * - 单进程 fetch 的上限约几千 QPS，够本项目演示；结论里会如实标注这一限制
 */

function arg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`)
  return index >= 0 ? process.argv[index + 1] : fallback
}

const url = arg('url')
const concurrency = Number(arg('concurrency', '50'))
const durationSec = Number(arg('duration', '30'))
const warmupSec = Number(arg('warmup', '5'))
const label = arg('label', url)
const outFile = arg('out', '')

if (!url) {
  console.error('missing --url')
  process.exit(1)
}

let latencies = []
let ok = 0
let fail = 0
let running = true

async function worker() {
  while (running) {
    const startedAt = performance.now()
    try {
      // manual：302 直接当成功，不跟到原始长链接
      const res = await fetch(url, { redirect: 'manual' })
      await res.arrayBuffer()
      if (res.status >= 200 && res.status < 400) {
        ok += 1
      } else {
        fail += 1
      }
    } catch {
      fail += 1
    }
    latencies.push(performance.now() - startedAt)
  }
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.floor(sorted.length * p))
  return sorted[index]
}

// 预热：把缓存/连接池打热，避免把冷启动算进结果
await new Promise((resolve) => setTimeout(resolve, warmupSec * 1000))
latencies = []
ok = 0
fail = 0

const startedAt = performance.now()
const workers = Array.from({ length: concurrency }, () => worker())
await new Promise((resolve) => setTimeout(resolve, durationSec * 1000))
running = false
await Promise.all(workers)
const elapsedSec = (performance.now() - startedAt) / 1000

const sorted = latencies.slice().sort((a, b) => a - b)
const total = ok + fail
const qps = elapsedSec > 0 ? total / elapsedSec : 0
const errorRate = total > 0 ? (fail / total) * 100 : 0
const avg = sorted.length > 0 ? sorted.reduce((sum, v) => sum + v, 0) / sorted.length : 0

const result = {
  label,
  url,
  concurrency,
  durationSec,
  total,
  ok,
  fail,
  qps: Number(qps.toFixed(1)),
  errorRate: Number(errorRate.toFixed(2)),
  avgMs: Number(avg.toFixed(2)),
  p50: Number(percentile(sorted, 0.5).toFixed(2)),
  p95: Number(percentile(sorted, 0.95).toFixed(2)),
  p99: Number(percentile(sorted, 0.99).toFixed(2)),
  maxMs: Number((sorted[sorted.length - 1] ?? 0).toFixed(2)),
}

console.log(
  `| ${result.label} | ${result.concurrency} | ${result.qps} | ${result.p50} | ${result.p95} | ${result.p99} | ${result.errorRate}% |`,
)
console.log(JSON.stringify(result))

if (outFile) {
  const { writeFileSync, mkdirSync } = await import('node:fs')
  const { dirname } = await import('node:path')
  mkdirSync(dirname(outFile), { recursive: true })
  writeFileSync(outFile, JSON.stringify(result, null, 2))
  console.log(`saved: ${outFile}`)
}
