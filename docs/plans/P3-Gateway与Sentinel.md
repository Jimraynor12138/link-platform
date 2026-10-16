# P3（V3）方案：Gateway 路由/鉴权/限流 + Sentinel

> 本文是 P3 阶段的方案文档（入库权威版）。对应版本 V3；前置版本 V2（P2）。

---

## 1. 目标

让 `link-gateway` 从骨架变成统一入口：

1. **路由**：按路径前缀把请求导到 auth / core（含根路径跳转）。
2. **鉴权前置**：网关验签后把 userId/tenantId/权限以请求头透传，下游信任网关。
3. **收口**：收回 P2 遗留的 `/internal/**` 暴露面（网关不路由）。
4. **限流两层 + 熔断降级**：接入 Sentinel，并与 core 既有手写限流/降级闸门明确分工。

---

## 2. 路由表（已盘点全部 controller 路径）

| 路由（Path 谓词） | 目标 | StripPrefix | 鉴权 |
|---|---|---|---|
| `/auth/**` | `lb://link-auth` | 否（下游 `@RequestMapping("/auth")`） | 仅 `/auth/login`、`/auth/refresh` 免鉴权 |
| `/api/v1/**` | `lb://link-core` | 否（下游是 `/api/v1/...`） | 需鉴权 |
| `/{code:[0-9a-zA-Z]{1,16}}` | `lb://link-core` | 否 | **免鉴权**（访客跳转，最低优先级） |
| `/internal/**` | 无路由（网关过滤器直接 403） | — | 拒绝（P2-2 收口） |

**决策：跳转 `/{code}` 也走网关**（统一入口），作为最低优先级 fallback 路由。
代价是跳转链路多一跳；文档注明：生产可把根路径在 LB/Nginx 层直接导 core 绕过网关（或做跳转专用优化）。

**Swagger 不走网关**：`/v3/api-docs/**`、`/swagger-ui/**` 等保持直连 8081/8082 访问（避免为文档在网关再开一堆路由）。

**核心事实（盘点所得）**：core 的写接口限流拦截器 `RateLimitInterceptor` 只挂 `/api/v1/link/**`、`/api/v1/group/**`，**跳转链路 `/{code}` 刻意不限流**（用降级闸门保护 DB，而非 QPS 限流）。

---

## 3. 鉴权前置 + 身份透传

- **技术选型**：网关是 **WebFlux**，自写 `GlobalFilter`（`Ordered`）做三件事——白名单放行 → 验签 → 剥离伪造头 + 写真实身份头。**不用 servlet 那套**（common 的 `GlobalExceptionHandler` 是 `@RestControllerAdvice`，在 WebFlux 里无效）。
- **共享密钥**：网关与 auth 用同一 JWT 密钥，统一由环境变量 `LINK_AUTH_JWT_SECRET` 注入。沿用 P1 的 S1 教训：**不给默认值 + 启动期校验**。
- **透传 header**：`X-User-Id`、`X-User-Name`、`X-Tenant-Id`、`X-User-Permissions`（逗号分隔）。
- **⚠ 安全关键点**：转发前**必须剥离客户端传入的同名 header**，否则任何人可伪造身份。**必须有单测**。
- **下游改造**：
  - auth：新增"读网关身份头"的过滤器（header 完整时优先构建认证上下文），**保留原 token 解析链路**（直连/调试用）。
  - core：目前无 Security，读 `X-Tenant-Id` 注入 `TenantContext`（P4 才真正用于隔离）。
- **401/403 统一响应**：网关自己写 WebFlux 响应（HTTP 401/403 + `Result` 结构）。
- **局限（必须写进文档）**：本地开发 8081/8082 仍可直连，真隔离要靠网络层（P7 只暴露网关端口）。

---

## 4. 限流两层（用户选 C）

| 层 | 手段 | 职责 |
|---|---|---|
| 网关 | Sentinel Gateway（`GatewayFlowRule`，按 route/IP） | 粗粒度入口限流，挡住最外层 |
| 服务 | `spring-cloud-starter-alibaba-sentinel` + `@SentinelResource` | 细粒度到具体接口，`blockHandler`/`fallback` |

**与手写 `RateLimitInterceptor` 的分工（不删手写，两套并存）**：
- 手写：精确到"写接口 + IP + 滑动窗口"，业务语义自控、无外部依赖。
- Sentinel：资源维度 + 统计窗口 + 熔断 + 可视化动态调参。
- 二者职责不同，文档需对比并说明"何时用哪套"。

---

## 5. 熔断降级（Sentinel 断路器保护对象）

1. **网关 → 各服务**：路由级保护，服务不可用时快速失败。
2. **core → auth 的 Feign**：`feign.sentinel.enabled=true` + `fallbackFactory`（拿异常原因）。

**与手写 `DegradationGuard` 对比**：Sentinel 是"资源 + 统计窗口（慢调用/异常比例）"驱动、带半开探测；手写闸门是"Redis 异常即进降级态 + 信号量限并发 + fail-open"。二者适用面不同，文档对比（这是"与手写降级闸门对比"的卖点）。

**重试与熔断叠加**：P2-4 已让 Feign 对 5xx 抛 `RetryableException`（仅幂等读客户端）。熔断打开后请求快速失败、重试被立刻拒绝，两者不冲突，但文档要讲清时序。

---

## 6. Sentinel Dashboard（Docker）

- 镜像走 `docker.m.daocloud.io` 拉取再 retag（Docker Hub 直连不通）。
- **端口必须改 8858**（默认 8080 与网关冲突）；登录 sentinel/sentinel。
- 客户端：`spring.cloud.sentinel.transport.dashboard=localhost:8858`；transport 端口默认 8719，同机多服务会顺延。
- **规则存内存、Dashboard 重启丢** → 文档注明；生产接 Nacos 数据源（P3 不实现，但要讲清）。
- compose 放 `deploy/sentinel/docker-compose.yml`（与 `deploy/nacos/` 风格一致）。

---

## 7. 验收标准

- 存量回归：core `202/0/0/1`、auth `28/0/0/0`（**保持 V2 基线**；auth 过滤器改造若需调测试必须说明原因）
- 新增单测：网关鉴权（白名单/无 token 401/验签失败 401/**伪造 header 被剥离**/合法 token 写头）、auth 读头过滤器
- E2E 经网关 8080：登录/受保护访问/401/伪造 header 不采纳/`/internal/**` 被 403/限流触发/熔断触发
- 全量 `mvn clean install -Dfile.encoding=UTF-8` 六模块 BUILD SUCCESS

---

## 8. 坑清单（执行时对照）

1. 网关是 WebFlux：不能用 servlet 的 Filter/`@RestControllerAdvice`；common 的 `GlobalExceptionHandler` 在网关里无效
2. header 伪造：必须先剥离客户端传入的 `X-User-*`/`X-Tenant-*`
3. 共享 JWT 密钥：统一环境变量 `LINK_AUTH_JWT_SECRET`，不给默认值 + 启动期校验
4. 跳转 `/{code}` 与 `/auth/**`、`/api/v1/**` 冲突：路由顺序（跳转放最低优先级）
5. 网关需要 `spring-cloud-starter-loadbalancer`
6. Sentinel Dashboard 默认 8080 冲突 → 8858
7. Docker Hub 直连不通 → `docker.m.daocloud.io` + retag
8. `-Dfile.encoding=UTF-8` 运行硬性要求
9. `.ps1` 临时脚本输出用 ASCII
10. Sentinel transport 端口（8719）同机多服务顺延
11. `@SentinelResource` 的 `blockHandler` 签名必须与原方法匹配
12. 重试与熔断叠加的时序
13. 网关 `@SpringBootApplication` 没有 scanBasePackages（要用 common 的 Bean 需注意）
