# V3（P3）Gateway 路由/鉴权/限流 + Sentinel

> 日期：2026-10-16 ｜ 版本：V3 ｜ 对应阶段：P3
> 一句话：让 link-gateway 从骨架变成统一入口（路由 + 鉴权前置 + 身份透传），并接入 Sentinel 做两层限流与熔断降级。

---

## 1. 做了什么

1. **路由盘点**：梳理 auth/core 全部 controller 路径 → 产出路由表
2. **网关路由**：`/auth/**`、`/api/v1/**`、`/{code}`（跳转免鉴权）走 `lb://服务名`；`/internal/**` 不路由
3. **鉴权前置 + 身份透传**：网关 GlobalFilter 验签后把 `X-User-Id`/`X-User-Name`/`X-Tenant-Id` 写进请求头，下游信任网关
4. **共享密钥**：网关与 auth 统一用环境变量 `LINK_AUTH_JWT_SECRET`（顺带修复 P2 迁配置时 auth"生产无 secret 来源"的隐患）
5. **下游改造**：auth 新增 `GatewayAuthHeaderFilter`（读头构建认证，保留 token 直连路径）
6. **收口 P2-2**：网关不路由 `/internal/**`（外部 404）
7. **Sentinel 两层限流 + 熔断**：网关 `GatewayFlowRule`（入口）+ core `@SentinelResource`（服务）+ Feign `fallback`（降级）

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 网关 | `filter/GatewayAuthFilter`、`token/JwtTokenVerifier`、`token/GatewayTokenBlacklistChecker`、`config/JwtVerifyProperties`、`config/SentinelGatewayConfig`；pom 加 jjwt/redis/sentinel-gateway/loadbalancer |
| 公共 | `constant/GatewayAuthHeaders`（身份头常量） |
| core | `config/SentinelRulesConfig`、`client/AuthClientFallback`、`LinkController` 加 `@SentinelResource`；pom 加 sentinel |
| auth | `security/GatewayAuthHeaderFilter`；`SecurityConfig` 接入；`application.yml` 补 `link.auth.jwt.secret=${LINK_AUTH_JWT_SECRET}` |
| 部署 | `deploy/sentinel/README.md` + `run-dashboard.ps1`（Dashboard 无官方镜像，改 jar 直跑） |
| 测试 | `GatewayAuthFilterTest`（6 用例，含伪造头剥离） |

---

## 3. 决策与理由

| # | 决策 | 理由 |
|---|---|---|
| D1 | 网关验签 + 身份透传；auth 从"解 token"改为"读头"（保留 token 路径） | 鉴权前置到网关，下游不必重复解 token；保留 token 路径用于直连/调试与向后兼容 |
| D2 | 跳转 `/{code}` 也走网关，作为最低优先级 fallback + 免鉴权 | 统一入口；访客点击短链不该登录 |
| D3 | **网关只做"身份认证"，不做"授权"** | token 里不携带权限码（权限每次查库，见 M3），授权仍由各服务 `@PreAuthorize` 完成——网关管"你是谁"，服务管"你能干什么" |
| D4 | 共享 JWT 密钥统一用环境变量，无默认值 + 启动期校验 | 沿用 P1 的 S1 教训；网关与 auth 必须同密钥否则验签必失败 |
| D5 | Sentinel 规则代码加载（`@PostConstruct`）+ Dashboard 只做可视化 | 规则可复现、不依赖 Dashboard；Dashboard 默认规则存内存、重启丢 |
| D6 | 与手写 `RateLimitInterceptor`/`DegradationGuard` 并存，明确分工 | 手写=业务语义细粒度、无外部依赖；Sentinel=资源维度 + 统计窗口 + 熔断 + 可视化。两套职责不同（P3 卖点） |
| D7 | Dashboard 用 jar 直跑而非 Docker | Sentinel 无官方镜像，daocloud 白名单不含第三方 sentinel 镜像；官方文档本就 jar 直跑 |

---

## 4. 踩坑（现象 · 原因 · 解决）

1. **Gateway 的 `Path` 谓词不支持 `{1,16}` 花括号量词**（报 `Invalid mapping pattern`），而 core 的 `@GetMapping("/{code:[0-9a-zA-Z]{1,16}}")` 却能跑。解决：网关用 `[0-9a-zA-Z]+`（PathPattern 对 `{m,n}` 支持不一致，实测差异），core 保留 `{1,16}` 做最终长度校验。
2. **YAML 里 `{code:...}` 的 `{}` 是 flow 集合起始符**，不加引号配置解析失败。
3. **过滤器注册顺序**：`addFilterBefore(x, JwtAuthenticationFilter.class)` 要求 `JwtAuthenticationFilter` 已注册——把它的注册放前面。
4. **网关是 WebFlux**：common 的 `GlobalExceptionHandler`（servlet `@RestControllerAdvice`）在网关里完全无效，401/403 响应要自己写。
5. **`feign.sentinel.enabled` 是顶层 `feign.*` 配置**，不是 `spring.cloud.sentinel.feign.*`——写错位置 fallback 静默不生效。
6. **`com.alibaba.csp.sentinel.datasource.Converter` 找不到**：SCA 把 `sentinel-datasource-extension` 标 `<optional>` 不传递，但其自动配置又引用它 → 显式引入。
7. **不注册 `SentinelGatewayBlockExceptionHandler` 时，被限流请求返回"连接重置"（curl 显示 000）**，而不是 429——需显式注册 + `GatewayCallbackManager.setBlockHandler` 返回 JSON。
8. **伪造头是身份透传最大的坑**：网关必须先 `remove` 客户端传入的 `X-User-*`/`X-Tenant-*` 再写真实身份，否则任何人可伪造登录态（已写单测覆盖）。
9. **Docker Hub 直连不通 + daocloud 白名单**：Nacos 能拉（官方镜像在白名单），Sentinel Dashboard（无官方镜像）拉不了 → jar 直跑。

---

## 5. 验证数据

| 项 | 结果 |
|---|---|
| 全量单测 | **236 全绿**（gateway `6/0/0/0`、auth `28/0/0/0`、core `202/0/0/1`） |
| 经网关登录 | `code=0` |
| 带 token 访问 `/auth/me` | `tenantadmin / id=2 / tenant=1`（网关→auth 全链路通） |
| 无 token | `401` |
| **伪造头**（`X-User-Id=999`/`X-User-Name=hacker`） | 仍返回 `tenantadmin/2/1`（被剥离） |
| `/internal/ping` | `404`（网关不路由） |
| 跳转 `/abc123` | `404`（公开路由，来自 core） |
| **服务层限流**（core 直接 4 连发 create） | `0, A0429, A0429, A0429` |
| **网关层限流**（带 token 5 连发） | `200, 200, 429, 429, 429` + JSON `请求过于频繁，已被网关限流` |
| **熔断降级**（停 auth 后调 feign-ping） | `degraded:true, instance:UNAVAILABLE`（core 仍优雅返回） |

---

## 6. 学到了什么（面试要点）

1. **"身份认证 vs 授权"分层**：网关验签判断"你是谁"，服务用 `@PreAuthorize` 判断"你能干什么"。权限不放进 token（避免令牌膨胀 + 权限变更要等 token 过期），而是每次查库——这与"权限即时生效"是一体的取舍。
2. **身份透传必须防伪造**：下游信任 header 的前提是"请求只能来自网关"，且网关必须剥离客户端同名头。这是最容易漏的安全点。
3. **共享密钥要治理**：网关与 auth 同密钥，统一环境变量 + 启动期校验，否则"配错了哪一边"都极难排查。
4. **Sentinel 与手写限流/降级不是二选一**：手写闸门（业务语义、无依赖）与 Sentinel（资源维度、统计窗口、可视化、熔断）各管一层，讲清边界比"都上 Sentinel"值钱。
5. **optional 依赖 / 默认值要实测**：`sentinel-datasource-extension` 被标 optional、`feign.sentinel.enabled` 的位置、`{1,16}` 的支持差异——文档没写清就得跑起来验证。

---

## 7. 下一版预告（V4 / P4）

- **core 多租户**：`t_link`/`t_link_stats` 加 `tenant_id`、`channel`；创建/查询/跳转链路强制带租户条件（`TenantContext` 在 P3 已把租户注入，P4 消费它）。
- **渠道归因**：短链带渠道标识，点击按渠道归因。
- **API Key**：`t_api_key` 已建表，P4 做生成 + 签名校验 + 配额限流。
