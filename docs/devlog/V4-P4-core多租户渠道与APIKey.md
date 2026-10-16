# V4（P4）core 多租户 + 渠道 + API Key + 跳转防刷

> 日期：2026-10-16 ｜ 版本：V4 ｜ 对应阶段：P4
> 一句话：给 link-core 落地多租户 SaaS 能力——租户数据隔离、渠道归因、开放 API（API Key 签名 + 配额）、跳转防刷。

---

## 1. 做了什么

1. **DB 迁移**：`t_link`/`t_link_stats`/`t_group` 加 `tenant_id`；`t_link`/`t_link_stats` 加 `channel`；统计唯一键改含 channel、索引按租户重建
2. **多租户隔离**：`TenantContextFilter`（读 `X-Tenant-Id` 注入 `TenantContext`，兑现台账 P3-3）+ `TenantLineInnerInterceptor`（管理链路自动拼 `tenant_id`，平台级/无上下文不过滤）
3. **跳转豁免**：`LinkMapper` 自定义 `selectByCodeAndDomain`/`selectByCodeIn` 标注 `@InterceptorIgnore`（短码全局唯一，访客免租户）
4. **渠道归因**：`t_link.channel` + 统计落库按 `(link_id, date, channel)` 聚合（channel 是短链属性，flush 时从 t_link 回查，不塞消息体）
5. **开放 API**：`/open/**` 签名校验（HMAC-SHA256）+ 防重放（timestamp 窗口 + nonce SETNX）+ 配额（Redis INCR）；auth 提供 API Key 生成 + 内部验签/查租户接口，core 通过 Feign 调用
6. **跳转防刷**：单 IP 对同短码窗口内统计去重（Redis SETNX，fail-open）

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| DB | `deploy/sql/03-tenant-schema.sql`（ALTER 加列 + 索引调整） |
| core | `common/context/TenantContextFilter`、`config/CoreTenantLineHandler`、`open/{OpenApiController,OpenApiAuthInterceptor,ApiQuotaService}`、`common/ratelimit/RedirectAntiAbuseGuard`；`LinkDO/GroupDO/LinkStatsDO` 加字段；`LinkMapper` 自定义豁免方法；`StatsSnapshotServiceImpl` 携带租户/渠道 |
| auth | `InternalController` 加 `/internal/tenants/{id}`、`/internal/api-keys/{accessKey}`；`ApiKeyService`/`ApiKeyController`（生成 access_key+secret） |
| common | `dto/TenantInfo`、`dto/ApiKeyInfo`（服务间契约） |
| 网关 | `/open/**` 路由 + 白名单放行 |
| 测试 | `CoreTenantLineHandlerTest`(6)、`TenantContextFilterTest`(3)、`OpenApiAuthInterceptorTest`(5)、`RedirectAntiAbuseGuardTest`(4) |

---

## 3. 决策与理由

| # | 决策 | 理由 |
|---|---|---|
| D1 | 短码全局唯一，隔离只在管理链路 | 访客点击 `/{code}` 无登录、无从定租户；短码全局唯一则跳转直接命中 |
| D2 | 渠道是"短链属性"而非"点击属性" | channel 创建时固定，统计落库时从 t_link 回查即可，不必塞进每条点击消息 |
| D3 | API Key 鉴权放 core `/open/**`（网关只放行） | API Key（无状态签名）与 JWT（会话 token）是两种不同性质凭证；网关保持"JWT 身份鉴权"单一职责 |
| D4 | 验 key/查配额走 Feign 调 auth | `t_api_key`/`t_tenant` 是 auth 域；把 P2/P3 的探活 Feign 升级为真实业务调用 |
| D5 | Feign 降级 fail-closed（返回失败） | 验 key/查配额是鉴权/配额调用，下游不可用时"假装成功"会放行未验证请求 |
| D6 | 跳转防刷是"统计去重"不是"限流拒绝" | 跳转是核心读服务，访客点了就必须跳；只在统计上报前去重 |
| D7 | API Key secret 存明文（可检索） | HMAC 验签需要服务端持有 secret 计算签名（与密码存摘要不同）；生产应加密，本项目如实标注 |

---

## 4. 踩坑（现象 · 原因 · 解决）

1. **MP 多租户拦截器把 `getTenantId()==null` 生成成 `tenant_id = NULL`**（SQL 恒假 → 查不到任何数据）。原以为"返回 null = 不过滤"，实测平台管理员/无上下文都查不到全量。解决：把"不过滤"逻辑移到 `ignoreTable()`（返回 true 才真正跳过），并用直连 core + 不同 `X-Tenant-Id` 头做了 4 组对照验证。
2. **Feign 客户端注入 `WebConfig`（WebMvcConfigurer）造成循环依赖**（`WebConfig → 拦截器 → AuthClient → EnableWebMvcConfiguration → WebConfig`），应用启动失败。解决：拦截器用 `ObjectProvider<AuthClient>` 惰性注入打破环。
3. **`channel` 唯一键列必须 `NOT NULL DEFAULT ''`**：MySQL 唯一索引把 NULL 视为"互不相同"，若允许 NULL，"无渠道"统计会出多行、upsert 失效。
4. **`-am` 缺失用旧 common jar**：core 单测编译报"找不到 com.aaf.link.common.dto"（本地仓库还是没 dto 包的旧 common），带 `-am` 重建。
5. **统计是 MQ 异步**：消费者写 `t_link_stats` 没有租户上下文，tenant_id/channel 在 flush 时用 `selectByCodeIn` 回查 `t_link` 冗余写入，而不是塞进每条消息。

---

## 5. 验证数据

| 项 | 结果 |
|---|---|
| 全量单测 | **254 全绿**（gateway `6/0/0/0`、auth `28/0/0/0`、core `220/0/0/1`） |
| 多租户隔离（直连 core 带不同 X-Tenant-Id） | 租户1→6 条、租户2→0 条、平台(0)→全量、无头→全量 |
| 创建短链落库 | `tenant_id=1, channel=email` ✅ |
| 租户二看不到租户一的链 | ✅ `False` |
| 平台管理员看全量 | ✅ `True` |
| 跳转 `/code` 免租户 | ✅ 302 |
| API 签名（合法/错签） | ✅ `code=0` / `401` |
| API 配额（quota=1 连打两次） | ✅ `ok, 429` |

---

## 6. 学到了什么（面试要点）

1. **多租户拦截器的"不过滤"要放对地方**：`getTenantId()` 返回 null 不是"跳过"，MP 会生成 `tenant_id = NULL`（恒假）。真正跳过是 `ignoreTable()` 返回 true。这是靠"直连 core + 伪造不同 X-Tenant-Id 头做对照"才定位出来的——单看代码和单测都发现不了。
2. **短码全局唯一让"跳转"天然免租户**：多租户隔离的边界是"管理链路"，跳转是"无租户上下文"的读路径，二者要分清楚。
3. **Feign 客户端别塞进 WebMvcConfigurer**：Feign → MVC → WebConfig → 拦截器 → Feign 是典型循环依赖，用 `ObjectProvider`/`@Lazy` 惰性注入打破。
4. **API Key 的 secret 与密码不同**：密码存摘要（单向），API Key 的 secret 是双方共享的对称密钥，服务端必须可检索才能验签；生产要加密存储。
5. **渠道是"短链属性"不是"点击属性"**：归因维度挂在实体上，统计冗余落库即可，不必把每条点击消息都带全上下文。

---

## 7. 下一版预告（V5 / P5）

- **Kafka 统计链路**：与 RabbitMQ 可切换的消息抽象，死信/幂等复用，讲清两种 MQ 的取舍。
