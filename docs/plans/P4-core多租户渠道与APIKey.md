# P4（V4）方案：core 多租户 + 渠道 + API Key + 跳转防刷

> 本文是 P4 阶段的方案文档（入库权威版）。对应版本 V4；前置版本 V3（P3）。

---

## 1. 目标

让 link-core 从"单租户短链引擎"变成"多租户 SaaS 核心"：

1. **多租户隔离**：`t_link`/`t_link_stats`/`t_group` 加 `tenant_id`；短码保持全局唯一（跳转免租户），隔离只作用于创建/查询/统计等管理链路。
2. **渠道归因**：`t_link` 加 `channel`，`t_link_stats` 按"短链+日期+渠道"聚合。
3. **开放 API**：core 独立 `/open/**` 入口，API Key + HMAC 签名 + 防重放 + 配额。
4. **跳转防刷**：单 IP 对同一短码高频点击轻量限流/去重，防统计污染。

---

## 2. 四个已确认的选择

| 问题 | 选择 |
|---|---|
| 短码唯一性 / 租户隔离边界 | 短码全局唯一，隔离只在管理链路（跳转 `/{code}` 免租户） |
| 渠道归因粒度 | `t_link.channel` + `t_link_stats` 加 channel 维度 |
| API Key 落点 | core 独立 `/open/**`，网关只放行不验签 |
| 防刷范围 | 跳转链路防刷（读侧，防统计污染） |

---

## 3. 数据模型（`deploy/sql/03-tenant-schema.sql`）

| 表 | 变更 |
|---|---|
| `t_link` | + `tenant_id`（BIGINT NOT NULL DEFAULT 0）、+ `channel`（VARCHAR(32) NOT NULL DEFAULT ''） |
| `t_link_stats` | + `tenant_id`、+ `channel`；唯一键 `uk_link_date(link_id,date)` → `uk_link_date_channel(link_id,date,channel)` |
| `t_group` | + `tenant_id`；唯一键 `uk_gid(gid)` → `uk_tenant_gid(tenant_id,gid)`（分组按租户隔离） |
| 索引 | `t_link` 加 `idx_tenant_create(tenant_id,del_flag,create_time)`、`idx_tenant_gid(tenant_id,gid,del_flag)`，删旧 `idx_gid_create` |

- **channel 用 `NOT NULL DEFAULT ''`**：MySQL 唯一索引把 NULL 视为"互不相同"，若允许 NULL，"无渠道"统计会出现多行、upsert 失效；用空串规避。
- 库为空、无历史数据，无需回填；脚本是一次性迁移（ALTER）。

---

## 4. 多租户隔离设计

- **core 新增 `TenantContextFilter`**：读网关透传的 `X-Tenant-Id` → `TenantContext`，`finally` 清理（兑现台账 P3-3）。
- **`MybatisPlusConfig` 加 `TenantLineInnerInterceptor` + `CoreTenantLineHandler`**：
  - `getTenantId()`：从 `TenantContext` 取；**为空或 `PLATFORM_TENANT_ID=0` 返回 null（关闭过滤，平台管理员看全量）**。
  - `ignoreTable()`：`t_sequence`、`t_segment`（无 tenant_id 字段的表）豁免。
  - **跳转豁免**：`LinkMapper` 的按 code 查询方法加 `@InterceptorIgnore(tenantLine = "1")`——访客无租户上下文，否则跳转 404。
- **统计是 MQ 异步**：点击消息体带 `tenant_id`、`channel`，消费者按消息字段写入，不依赖 ThreadLocal。

---

## 5. 渠道归因

- `LinkCreateReqDTO` 加 `channel`（可选，`@Size(max=32)`，空转 `''`）。
- 跳转点击上报消息带 `channel`；`t_link_stats` 按 `(link_id, date, channel)` 聚合；统计查询提供渠道维度（为 P6 看板铺数据）。

---

## 6. API Key（core `/open/**`）

- 网关加 `/open/**` 路由 → `lb://link-core`（排前面，避免被 `/{code}` fallback 吞）+ 白名单放行（不要求 JWT）。
- core `OpenApiController` + 签名校验，校验顺序：access_key 存在 → status 启用 → 未过期 → 验签（HMAC-SHA256）→ 防重放（timestamp ±5min + nonce Redis SETNX）→ 配额（Redis INCR + 过期窗口，超额 429）。
- `t_api_key` 读取走 **Feign 调 auth 的 `/internal/api-key/verify`**（auth 返回 secret/status/tenant_id/quota/expire_at），core 不直接查表；同时 Feign 查 `t_tenant.quota_per_day` 做租户每日配额——把 P2/P3 的探活 Feign 升级为真实业务调用。
- 配额扣减 fail-closed（下游不可用即拒绝，见 V3 修复）。

---

## 7. 跳转防刷

- 复用 `SlidingWindowRateLimiter` + Redis：单 IP 对同一 `code` 的点击频率，超限丢弃本次统计上报（不放行影响跳转主链路）。
- 语义区分：这是"防统计污染"，不是"跳转 QPS 限流"（跳转链路 P0 就刻意不限流）；写接口防刷已有（RateLimitInterceptor + Sentinel）。

---

## 8. 验收标准

| 项 | 标准 |
|---|---|
| 存量回归 | core `202/0/0/1`、auth `28/0/0/0`、gateway `6/0/0/0`（236 不因 P4 变红） |
| 多租户隔离 | 租户 1 建的短链租户 2 查不到；平台管理员看全量；跳转 `/{code}` 免租户直达 |
| 渠道归因 | 创建带 channel，点击后统计按渠道聚合 |
| API Key | 合法签名通过、错签 401、过期/停用 403、超配额 429；网关放行 `/open/**` |
| 防刷 | 单 IP 高频点击同一短码被限流/去重，不影响正常跳转 |

---

## 9. 坑清单

1. 多租户拦截器会拦所有表，跳转按 code 查必须豁免，否则访客跳转 404
2. 平台级（tenant_id=0）要"不过滤"（`getTenantId()` 返回 null）
3. 统计是 MQ 异步，消息体要带 tenant_id/channel
4. core 信任 `X-Tenant-Id` 的前提（直连伪造，P3-1 → P7）
5. `uk_link_date` 改含 channel 后，upsert 维度变了；channel 用 `''` 而非 NULL
6. `t_group` 'default' 种子只有一条，多租户下每租户自动建自己的默认分组
7. API Key 防重放要"时间窗 + nonce 去重"两者都有
8. 配额扣减用 Redis 原子操作，明确 fail-open/closed
9. secret 不落日志；core 不直接查 `t_api_key`（走 Feign，表归属 auth）
10. 跳转防刷轻量、fail-open，不拖慢跳转
11. Feign 配额校验是读接口（幂等），沿用"安全默认不重试"，避免配额重复扣减
