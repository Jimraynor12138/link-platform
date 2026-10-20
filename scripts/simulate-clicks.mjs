#!/usr/bin/env node
/**
 * 模拟"真实用户点击"（走完整链路：网关 → core → 防刷 → MQ → 消费 → Redis → 每 2s flush → MySQL）
 *
 * 用法：
 *   node scripts/simulate-clicks.mjs [轮数]
 *   node scripts/simulate-clicks.mjs 5        # 5 轮 × 每轮每个短码 1 次
 *
 * 为什么要"一轮点所有码 + 轮间等待"：
 *   P4 的跳转防刷是「同 IP + 同码 + 1 秒内只记一次统计」，
 *   所以单码狂点不会涨 PV；轮询所有码、每轮间隔 > 1 秒，才能让每个码都记上。
 *
 * 注意：这是真实点击，只影响**今天**这一行；历史数据由
 *   `deploy/sql/seed/01-stats-backfill.sql` 回填（模拟值，脚本里已注明）。
 */

const BASE = process.env.BASE || 'http://localhost:8080'
const ROUNDS = Number(process.argv[2] || 3)
const GAP_MS = 1200

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const loginRes = await fetch(`${BASE}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'superadmin', password: '123456' }),
})
const token = (await loginRes.json()).data.accessToken

const pageRes = await fetch(`${BASE}/api/v1/link/page`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ current: 1, size: 100 }),
})
const links = (await pageRes.json()).data.records ?? []
const codes = links.filter((l) => l.enableStatus === 0).map((l) => l.code)

console.log(`可点击短码 ${codes.length} 个：${codes.join(', ')}`)
console.log(`开始 ${ROUNDS} 轮，每轮间隔 ${GAP_MS}ms（避开防刷窗口）\n`)

let ok = 0
let fail = 0
for (let round = 1; round <= ROUNDS; round++) {
  const line = []
  for (const code of codes) {
    try {
      const res = await fetch(`${BASE}/${code}`, { redirect: 'manual' })
      if (res.status === 302) ok += 1
      else fail += 1
      line.push(`${code}:${res.status}`)
    } catch {
      fail += 1
      line.push(`${code}:ERR`)
    }
  }
  console.log(`第 ${round} 轮 -> ${line.join(' ')}`)
  if (round < ROUNDS) await sleep(GAP_MS)
}

console.log(`\n完成：成功 ${ok} 次、失败 ${fail} 次`)
console.log('等 3 秒让快照 flush 落库（每 2s 一次），再看板/接口就会涨。')

// 多等一会儿，方便脚本直接接验证命令
await sleep(3000)
