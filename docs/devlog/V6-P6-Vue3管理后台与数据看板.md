# V6（P6）Vue3 管理后台 + 数据看板

> 日期：2026-10-17 ｜ 版本：V6 ｜ 对应阶段：P6
> 一句话：给已跑通的后端补上界面——登录、短链管理、分组、数据看板、平台侧管理，并把前端必需的 4 处后端缺口与 2 处限流阈值一并补齐。

---

## 1. 做了什么

1. **后端补齐（4 处 + 2 处限流）**：
   - `LinkRespDTO` 增加 `channel`（列表展示渠道）
   - `GET /auth/tenants`（平台看租户列表）
   - `GET /auth/api-keys?tenantId=`（平台看 Key 列表，**不返回 secret**）
   - 看板聚合：`GET /api/v1/stats/overview`（按日 PV/UV）+ `/channels`（按渠道汇总），数据源 `t_link_stats`
   - 网关 `/api/v1/**` QPS **2 → 50**、core `createLink` QPS **1 → 10**（否则 UI 一页并发请求就被 429）
2. **前端工程 `link-web/`**：Vue 3 + Vite 5 + TS + Element Plus + Tailwind CSS + Pinia + Router 4 + Axios + ECharts
3. **登录态**：access + refresh，401 **单飞刷新 + 重放原请求**，token 持久化
4. **六个页面**：登录、短链管理、分组管理、数据看板、租户管理（平台）、API Key（平台）
5. **视图区分**：`tenantId === 0` 显示平台菜单；路由守卫拦截平台专属页

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 后端 | `TenantController`/`TenantService`/`TenantVO`、`ApiKeyVO` + `listByTenantId`、`DailyStatsVO`/`ChannelStatsVO` + Mapper 聚合、`StatsController` 两个新接口、两处限流阈值 |
| 前端 | `link-web/`（api / stores / router / layout / components / 6 个 views） |
| 测试 | `StatsServiceImplTest` +3、`TenantServiceTest` +2、`ApiKeyServiceListTest` +2 |
| 文档 | 本 devlog、测试报告、`docs/plans/P6-*.md` |

---

## 3. 决策与理由

| # | 决策 | 理由 |
|---|---|---|
| D1 | Element Plus + Tailwind 并存 | Element Plus 提供表格/表单/弹窗/日期选择（后台刚需），Tailwind 负责布局与视觉；两者不冲突 |
| D2 | 独立 `link-web/` + Vite proxy | 前后端分离、开发期免 CORS；生产静态托管留给 P7 |
| D3 | 构建只跑 `vite build` | 演示项目优先保证可交付，不把 `vue-tsc` 严格类型检查作为构建门槛 |
| D4 | 看板新加 DB 聚合接口，不复用 `/stats/{code}` | 后者是"单码 + 单日 + Redis 数据源"，做不了跨日趋势与渠道汇总 |
| D5 | 聚合 SQL **不加** `@InterceptorIgnore` | 让多租户拦截器自动拼 `tenant_id`：租户看自己、平台看全量，天然隔离 |
| D6 | refresh 单飞（共享同一个 Promise） | 并发 401 各自刷新会打爆 auth 且相互覆盖 token |
| D7 | 上调 Sentinel 阈值 | QPS=2/1 是 P3 为"演示限流"设的，管理后台正常操作就会被误伤 |

---

## 4. 踩坑（现象 · 原因 · 解决）

1. **`vue-router@5` 与 Vite 5 冲突**：`npm install vue-router` 装到 5.x，peer 要求 Vite 7/8，`ERESOLVE` 直接失败。解决：显式 `vue-router@4`。
2. **`Result.code` 是字符串**：后端 `code` 为 `"0"`（String），前端若判 `code === 0` 永远不相等。解决：统一判 `code !== '0'`。
3. **网关 QPS=2 会把 UI 卡死**：一个列表页会并发拉 短链/分组/统计 三个请求，第 3 个就 429。解决：调到 50（并在注释里保留"演示时可调回"）。
4. **看板没有数据源**：`/api/v1/stats/{code}` 走 Redis 单码单日；看板需要 `t_link_stats` 的跨日/渠道聚合。解决：新增两个聚合接口。
5. **`StatsServiceImpl` 构造器变更**：注入 `LinkStatsMapper` 后，`StatsServiceImplTest` 的手工构造要同步补 mock（否则编译失败）。
6. **浏览器自动化装不上**：`npm install -g agent-browser` 报 `ECONNRESET`（全局包下载被网络中断），无法做真浏览器截图。**降级**为 `vite build` + Vite 模块转换检查 + 接口验证，并在测试报告如实标注。
7. **表格里 `enableStatus` 的语义**：`0` 是启用、`1` 是停用（容易反），前端 Tag/按钮文案按这个映射写。

---

## 5. 验证数据

| 项 | 结果 |
|---|---|
| 后端单测 | **281 全绿**（gateway `6/0/0/0`、auth `37/0/0/0`、core `238/0/0/1`） |
| 前端构建 | `npm run build` → `✓ built in 15.78s`，产出 `dist/`（含按路由拆分的 chunk） |
| 模块转换 | 15 个入口/页面 SFC 经 Vite 转换全部 **200、无编译错误** |
| 接口联调（经网关 8080） | 登录 `code=0`、租户列表 `2` 条、API Key `8` 条、看板 overview `2` 条、channels `2` 条、短链分页 `total=14`，且返回体**含 `channel` 字段** |
| 浏览器冒烟 | ⚠️ 未执行（`agent-browser` 安装失败，见踩坑 6） |
| P6-4 复审修复 | 新增 `apikey:manage` 权限 + 按本人租户收敛：租户列表 `200`、跨租户创建 `403`、本人创建 `200`、只读 `403`、平台查全部 `200`（6 项 E2E） |

---

## 6. 学到了什么（面试要点）

1. **"前后端分离"的第一个坑往往不是代码，而是契约**：`code` 是字符串、`enableStatus` 是 0/1、`LocalDateTime` 的序列化格式——这三个不对，界面就是空白。
2. **限流阈值要跟着使用场景走**：QPS=2 适合演示"被限流的样子"，但不适合真实操作；阈值是产品决策，不是纯技术参数。
3. **看板的数据源要和实时链路分开**：Redis 适合"今天这一刻的实时数"，MySQL 快照表适合"跨天趋势与聚合"；混用会导致图表要么没数据、要么口径不一致。
4. **多租户隔离对聚合查询同样生效**：只要不写 `@InterceptorIgnore`，`GROUP BY` 查询也会被自动加上 `tenant_id` 条件——这比自己手写 `where tenant_id=?` 更难写错。

---

## 7. 下一版预告（V7 / P7）

- **Docker Compose 编排 + 压测对比**：把 gateway/auth/core/web 一起容器化，做"拆分前后 QPS/RT"的对比数据，并写清"多一跳的代价"。
