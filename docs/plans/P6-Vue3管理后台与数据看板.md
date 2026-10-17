# P6（V6）方案：Vue3 管理后台 + 数据看板

> 本文是 P6 阶段的方案文档（入库权威版）。对应版本 V6；前置版本 V5（P5）。

---

## 1. 目标

把 V0~V5 已就绪的后端能力（认证、短链、分组、统计、多租户、API Key）串成**可用的管理后台**，补齐"只有 API 没有界面"的缺口。

---

## 2. 四个已确认的选择

| 问题 | 选择 |
|---|---|
| UI 组件库 | **Element Plus** |
| 页面范围 | 登录 + 布局 + 短链管理、数据看板、分组管理、平台侧（租户 + API Key）**全做** |
| 工程形态 | **独立 `link-web/` + Vite 代理**（dev 转发 `/auth` `/api` → 网关 8080） |
| 登录态 | **access + refresh 自动续期**（401 单飞刷新 + 重放）+ 平台/租户视图区分 |

**不做**：Docker 编排与压测（P7）、移动端适配、Sentinel 规则持久化（P3-2）。

---

## 3. 技术栈

- Vue 3 + Vite 5 + TypeScript 5（构建只跑 `vite build`，不跑 `vue-tsc` 严格检查）
- Element Plus（表格/表单/弹窗/日期选择）+ **Tailwind CSS**（布局与视觉）
- Pinia + Vue Router 4 + Axios
- **vue-echarts**（ECharts 5 封装）+ `lucide-vue-next`（图标）
- 环境：Node v26.5.0 / npm 11.17.0 / registry `https://registry.npmmirror.com/`

---

## 4. 后端补齐（4 处 + 2 处限流）

### 4.1 `LinkRespDTO` 增加 `channel`

`LinkServiceImpl.toRespDTO` 补 `resp.setChannel(link.getChannel())`。`LinkDO` 已有 `channel`，DB 无需改动。

### 4.2 `GET /auth/tenants`（平台）

- 新建 `service/TenantService`：`List<Tenant> listAll()`（`TenantMapper.selectList`，`@TableLogic` 自动过滤 `del_flag`）
- 新建 `controller/TenantController`，`@PreAuthorize("hasAuthority('tenant:manage')")`
- 新建 `vo/TenantVO`：`id / name / status / quotaPerDay`（**不暴露 delFlag/updateTime**）

### 4.3 `GET /auth/api-keys?tenantId=`（平台）

- `ApiKeyService.listByTenantId(Long tenantId)`
- `ApiKeyController` 增加 `GET`，同样 `tenant:manage`
- 新建 `vo/ApiKeyVO`：`accessKey / quota / expireAt / status / createTime`（**绝不返回 secret**）

### 4.4 看板聚合（core）

- `LinkStatsMapper` 增两个 `@Select` 聚合方法（**不加 `@InterceptorIgnore`**，让多租户拦截器自动拼 `tenant_id`：租户看自己、平台看全量）：
  - 按日：`SELECT date, SUM(pv) pv, SUM(uv) uv FROM t_link_stats WHERE date BETWEEN #{from} AND #{to} GROUP BY date ORDER BY date`
  - 按渠道：`SELECT channel, SUM(pv) pv, SUM(uv) uv FROM t_link_stats WHERE date BETWEEN #{from} AND #{to} GROUP BY channel ORDER BY pv DESC`
- 新 VO：`DailyStatsVO(date,pv,uv)`、`ChannelStatsVO(channel,pv,uv)`
- `StatsService` + `StatsServiceImpl` 增两方法（**构造器新增 `LinkStatsMapper`，`StatsServiceImplTest` 需同步补 mock**）
- `StatsController` 增 `GET /api/v1/stats/overview` 与 `/channels`（`from`/`to` 为 `yyyy-MM-dd`，默认近 7 天）

### 4.5 限流阈值（否则 UI 不可用）

| 位置 | 现状 | 调整为 |
|---|---|---|
| `SentinelGatewayConfig` 的 `link-core-api`（`/api/v1/**`） | QPS **2** | **50** |
| `SentinelRulesConfig` 的 `createLink` | QPS **1** | **10** |

注释写明"为让 UI 可用；压测/演示时仍可调回"。

---

## 5. 前端架构

```
link-web/
├── vite.config.ts        # Vue 插件 + @ 别名 + /auth /api 代理到 8080 + allowedHosts
├── tailwind.config.js / postcss.config.js
└── src/
    ├── api/{http,auth,link,group,stats,tenant}.ts
    ├── stores/auth.ts
    ├── router/index.ts
    ├── layout/{index,Sidebar,Header}.vue
    ├── components/ChartPanel.vue
    └── views/{login,link,group,dashboard,tenant,apikey}/index.vue
```

### 5.1 登录态（核心）

- token 存 Pinia + localStorage
- 请求拦截注入 `Authorization: Bearer <accessToken>`
- 响应拦截：
  - HTTP **401** → 单飞刷新（模块级 `refreshing: Promise<string> | null`）→ 重放原请求
  - HTTP **403/429** → 提示并拒绝
  - HTTP 200 但 `code !== "0"` → `ElMessage.error(message)`
- 刷新失败 → 清空并跳 `/login`
- `/auth/login`、`/auth/refresh` 不触发刷新

> 注意：后端 `Result.code` 是**字符串**（`"0"` 表示成功）。

### 5.2 视图区分

- `GET /auth/me` 拿 `tenantId` 与 `permissions`
- `tenantId === 0` → 显示"租户管理""API Key"菜单；其余隐藏
- 后端 403 兜底，前端只是体验优化

---

## 6. 页面清单

| 页面 | 要点 |
|---|---|
| 登录 | 居中卡片、用户名/密码、演示账号提示（superadmin / tenantadmin） |
| 短链管理 | 分页表格（短码可复制、完整短链、原始 URL、分组、**渠道**、状态、有效期）+ 新建/编辑弹窗 + 启停 + 删除 + 按分组筛选 |
| 分组管理 | 列表 + 新建 + 删除（提示"分组下短链不受影响"） |
| 数据看板 | 日期范围（默认近 7 天）+ PV/UV 折线 + 渠道柱状 + 空态 |
| 租户管理（平台） | 表格：ID/名称/状态/日配额 |
| API Key（平台） | 租户筛选 + 生成（secret 仅一次可见）+ 列表（不含 secret） |

---

## 7. 验收标准

| 项 | 标准 |
|---|---|
| 前端构建 | `npm install` + `npm run build` 成功 |
| 后端回归 | 存量 **269** 不红；新增租户/APIKey/聚合/`channel` 单测 |
| 登录 | superadmin / tenantadmin 均可登录，刷新页面登录态保持 |
| 自动续期 | accessToken 失效后自动 refresh 并重放成功 |
| 视图 | superadmin 见平台菜单；tenantadmin 不见 |
| 短链 | 列表/新建/编辑/启停/删除全通（含渠道） |
| 看板 | 有数据渲染、无数据空态 |
| 平台侧 | 租户列表、API Key 生成/列表（列表无 secret） |

---

## 8. 坑清单

1. **网关 `/api/v1/**` QPS=2 会卡死 UI** → 调到 50
2. **`LinkRespDTO` 缺 channel** → 补
3. **看板不能用 `/api/v1/stats/{code}`**（单码单日 + Redis）→ 新加 DB 聚合接口
4. **refresh 并发风暴** → 单飞（共享同一个 Promise）
5. **刷新失败要彻底清理**（token + user + 路由）
6. **Vite proxy 只对 dev 生效**（生产 P7 再托管）
7. **ECharts 容器必须有高度**，否则白屏
8. **日期格式**：`LocalDateTime` → `yyyy-MM-ddTHH:mm:ss`，DatePicker 同格式
9. **聚合 SQL 不要加 `@InterceptorIgnore`**，否则多租户隔离失效
10. **`Result.code` 是字符串**，前端判 `code === '0'` 不能判数字
11. **构建不跑 `vue-tsc`**（类型从简，避免阻塞）
12. **`.ps1` ASCII、`-Dfile.encoding=UTF-8`** 等既有约束继续生效
