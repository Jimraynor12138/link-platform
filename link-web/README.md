# link-web（V6 管理后台）

Vue 3 + Vite 5 + TypeScript + Element Plus + Tailwind CSS + ECharts 的管理后台，对接 V0~V5 的后端能力。

## 启动

前置：后端已启动（gateway `8080` / auth `8081` / core `8082`）+ Nacos + MySQL + Redis。

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # 产物输出到 dist/
```

开发期通过 Vite proxy 把 `/auth`、`/api` 转发到网关 `http://localhost:8080`，无需处理 CORS。

## 演示账号

| 账号 | 密码 | 视图 |
|---|---|---|
| `superadmin` | `123456` | 平台管理员：可看到「租户管理」「API Key」菜单 |
| `tenantadmin` | `123456` | 租户管理员：只看本租户数据 |

## 功能

| 页面 | 说明 |
|---|---|
| 登录 | 用户名/密码；access token 失效时用 refresh **单飞刷新 + 重放原请求** |
| 短链管理 | 分页列表、按分组筛选、新建/编辑、启停、删除、复制短链、渠道展示 |
| 分组管理 | 列表、新建、删除 |
| 数据看板 | 日期范围 + PV/UV 折线 + 渠道效果柱状（数据源 `t_link_stats`） |
| 租户管理（平台） | 租户 ID/名称/状态/日配额 |
| API Key（平台） | 按租户筛选、生成（secret 仅一次可见）、列表（不返回 secret） |

## 目录

```
src/
├── api/          # axios 实例（401 单飞刷新）+ 各业务接口
├── stores/       # Pinia 登录态（token/user/permissions）
├── router/       # 路由表 + 守卫（未登录跳登录、平台页校验）
├── layout/       # 侧边栏 + 顶栏
├── components/   # ChartPanel（ECharts 薄封装，含空态）
└── views/        # login / link / group / dashboard / tenant / apikey
```

## 约定

- 后端统一响应 `Result` 的 **`code` 是字符串**，`"0"` 表示成功
- 菜单只是体验优化，权限边界仍在后端（403 兜底）
- 构建只跑 `vite build`（不做 `vue-tsc` 严格类型检查）
