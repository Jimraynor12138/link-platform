#!/usr/bin/env node
/**
 * 演示数据生成器
 *
 * 做两件事：
 *   1) 通过**真实接口**创建租户与短链（走发号器、多租户归属、权限校验，数据是"真的"）
 *   2) 生成 `deploy/sql/seed/01-stats-backfill.sql`，用 SQL 回填过去 14 天的点击统计
 *      —— 历史不可能靠"现在点"造出来，只能回填；这一步会在 SQL 里如实标注
 *
 * 用法：
 *   node scripts/seed-demo.mjs
 *   docker exec -i link-mysql mysql -uroot -p<密码> < deploy/sql/seed/01-stats-backfill.sql
 *
 * 幂等：租户已存在会跳过；短链按 description 去重，重复执行不会重复建链。
 */

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const BASE = process.env.BASE || 'http://localhost:8080'
const PWD = '123456'
const DAYS = 14

async function call(method, path, token, body) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body) headers['Content-Type'] = 'application/json'
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json().catch(() => null)
  return { status: res.status, body: json }
}

async function login(username, password = PWD) {
  const r = await call('POST', '/auth/login', null, { username, password })
  if (!r.body?.data?.accessToken) {
    throw new Error(`登录失败 ${username}: ${JSON.stringify(r.body)}`)
  }
  return r.body.data.accessToken
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// ---------------- 计划 ----------------
// 每个操作人负责一批短链；channel 用于看板的"渠道效果"
const TENANTS = [
  {
    name: '租户二',
    admin: 'tenant2admin',
    nickname: '租户二管理员',
    quota: 80000,
    links: [
      ['租户二-抖音投放', 'douyin', 160],
      ['租户二-微信推文', 'wechat', 110],
      ['租户二-线下物料', 'offline', 45],
    ],
  },
  {
    name: '租户三',
    admin: 'tenant3admin',
    nickname: '租户三管理员',
    quota: 50000,
    links: [
      ['租户三-邮件营销', 'email', 70],
      ['租户三-微信社群', 'wechat', 55],
    ],
  },
]

const PLATFORM_LINKS = [
  ['平台-双十一主会场', 'douyin', 220],
  ['平台-官网落地页', 'offline', 90],
  ['平台-合作伙伴推广', 'partner', 60],
]

const DEMO_TENANT_LINKS = [
  ['演示租户-抖音短视频', 'douyin', 260],
  ['演示租户-微信朋友圈', 'wechat', 190],
  ['演示租户-微博话题', 'weibo', 120],
  ['演示租户-小红书笔记', 'xiaohongshu', 150],
]

// ---------------- 执行 ----------------
const created = []
const codes = []

async function ensureTenant(t) {
  const res = await call('POST', '/auth/tenants', superadminToken, {
    name: t.name,
    quotaPerDay: t.quota,
    adminUsername: t.admin,
    adminPassword: PWD,
    adminNickname: t.nickname,
  })
  if (res.body?.code === '0') {
    console.log(`[tenant] 新建 ${t.name} (id=${res.body.data.tenantId}, admin=${t.admin})`)
  } else if (res.body?.code === 'A0409') {
    console.log(`[tenant] 已存在，跳过 ${t.name}`)
  } else {
    console.log(`[tenant] 失败 ${t.name}: ${JSON.stringify(res.body)}`)
    return false
  }
  return true
}

async function createLinks(token, owner, plan) {
  // 已有的 description 集合，用于幂等
  const page = await call('POST', '/api/v1/link/page', token, { current: 1, size: 100 })
  const existing = new Set((page.body?.data?.records ?? []).map((r) => r.description))

  for (const [title, channel, base] of plan) {
    if (existing.has(title)) {
      const hit = (page.body.data.records ?? []).find((r) => r.description === title)
      console.log(`[link] 已存在 ${title} -> ${hit?.code}`)
      created.push({ owner, title, channel, base, code: hit.code })
      codes.push(hit.code)
      continue
    }
    const res = await call('POST', '/api/v1/link/create', token, {
      originalUrl: `https://example.com/${encodeURIComponent(channel)}/${encodeURIComponent(title)}`,
      channel,
      validType: 1,
      description: title,
    })
    if (res.body?.code === '0') {
      const link = res.body.data
      console.log(`[link] 新建 ${title} -> ${link.code} (channel=${channel})`)
      created.push({ owner, title, channel, base, code: link.code })
      codes.push(link.code)
    } else {
      console.log(`[link] 失败 ${title}: ${JSON.stringify(res.body)}`)
    }
    // 避开网关 50 QPS 与 createLink 10 QPS 的限流
    await sleep(150)
  }
}

const superadminToken = await login('superadmin')
await createLinks(superadminToken, 'platform', PLATFORM_LINKS)
await createLinks(superadminToken, '平台', [])

// 演示租户（tenant 1）已有 tenantadmin
const demoToken = await login('tenantadmin')
await createLinks(demoToken, '租户一', DEMO_TENANT_LINKS)

// 新租户
for (const t of TENANTS) {
  const ok = await ensureTenant(t)
  if (!ok) continue
  try {
    const token = await login(t.admin)
    await createLinks(token, t.name, t.links)
  } catch (e) {
    console.log(`[link] ${t.name} 登录失败，跳过：${e.message}`)
  }
}

// ---------------- 生成回填 SQL ----------------
if (codes.length === 0) {
  console.log('没有可回填的短码，退出')
  process.exit(0)
}

const codeList = codes.map((c) => `'${c.replace(/'/g, "''")}'`).join(', ')

// n = 1..DAYS：**从昨天往前数**，不包含今天。
// 原因：upsertStats 是"只增不减"（pv = IF(new.pv >= pv, new.pv, pv)），
// 如果把今天也回填成几百，真实点击（Redis 里只有个位数）永远超过不了它，
// 就看不出"点一下数据涨一下"的效果。今天留给真实点击。
const days = Array.from({ length: DAYS }, (_, i) => `SELECT ${i + 1} AS n`).join(' UNION ALL ')

const sql = `-- ============================================================
-- 演示数据：回填过去 ${DAYS} 天点击统计（**不含今天**，由 scripts/seed-demo.mjs 生成）
--
-- ⚠️ 这是**回填的模拟历史**，不是真实点击。
--    今天这一行刻意留空，交给真实点击（跳转 -> MQ -> 消费 -> 每 2s flush）去累积，
--    否则 upsertStats 的"只增不减"规则会让真实点击永远显示不出来。
--
-- 数据特征：按渠道给不同量级 + 越接近今天越高 + 由 CRC32 产生确定性抖动
-- 可重复执行：先按短码删掉这些链的统计，再整批插入
-- ============================================================
SET NAMES utf8mb4;
USE \`link_platform\`;

DELETE FROM t_link_stats
WHERE link_id IN (SELECT id FROM t_link WHERE code IN (${codeList}));

INSERT INTO t_link_stats
    (link_id, tenant_id, code, channel, date, pv, uv, ip_cnt, default_cnt, referer_stats, device_stats)
SELECT
    x.id,
    x.tenant_id,
    x.code,
    x.channel,
    DATE_SUB(CURDATE(), INTERVAL x.n DAY),
    x.pv,
    GREATEST(1, FLOOR(x.pv * (0.35 + MOD(CRC32(CONCAT(x.code, 'uv', x.n)), 30) / 100))),
    GREATEST(1, FLOOR(x.pv * 0.85)),
    FLOOR(x.pv * 0.30),
    CONCAT('{"direct":', FLOOR(x.pv * 0.30), '}'),
    CONCAT('{"mobile":', FLOOR(x.pv * 0.72), ',"pc":', x.pv - FLOOR(x.pv * 0.72), '}')
FROM (
    SELECT
        l.id,
        l.tenant_id,
        l.code,
        l.channel,
        d.n,
        ROUND(
            (CASE l.channel
                WHEN 'douyin'      THEN 220
                WHEN 'wechat'      THEN 160
                WHEN 'weibo'       THEN 110
                WHEN 'xiaohongshu' THEN 140
                WHEN 'email'       THEN 80
                WHEN 'offline'     THEN 60
                WHEN 'partner'     THEN 50
                ELSE 40
            END) * (0.70 + MOD(CRC32(CONCAT(l.code, '-', d.n)), 60) / 100)
        ) + (${DAYS} - d.n) * 6 AS pv
    FROM t_link l
    JOIN (${days}) d
    WHERE l.code IN (${codeList})
      AND l.del_flag = 0
) x;
`

const outPath = 'deploy/sql/seed/01-stats-backfill.sql'
mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(outPath, sql)

console.log('\n===== 汇总 =====')
console.log(`短链 ${created.length} 条，短码：${codes.join(', ')}`)
console.log(`已生成：${outPath}`)
console.log('下一步：docker exec -i link-mysql mysql -uroot -p<密码> < deploy/sql/seed/01-stats-backfill.sql')
