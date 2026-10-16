# V1（P1）link-auth 认证链路与租户模型

> 日期：2026-10-16 ｜ 版本：V1 ｜ 对应阶段：P1
> 一句话：把空骨架的 `link-auth` 变成真正的认证服务，并把全项目包名与配置前缀规范化到统一命名空间。

---

## 1. 做了什么

分两个阶段，每阶段都有可验证的硬产出。

**阶段一：包名与配置前缀规范化（零行为变更）**
1. 盘点 `com.shortlink` 全量引用：涉及 **107 个文件**、`ShortLinkProperties` 被 **97+ 处**引用
2. 用脚本批量改名：`com.shortlink.common.*` → `com.aaf.link.common.*`；`com.shortlink.*` → `com.aaf.link.core.*`
3. 配置前缀 `shortlink.*` → `link.core.*`（yml 整块缩进重排 + `@ConditionalOnProperty` 前缀 + 占位符 + 配置类）
4. 类名规范化：`ShortLinkApplication` → `LinkCoreApplication`、`ShortLinkProperties` → `LinkCoreProperties`
5. 补上 `scanBasePackages = "com.aaf.link"`（还掉 P0 预告的债）
6. 回归：198 个单测与基线**一字不差**

**阶段二：link-auth 认证与租户模型**
1. 依赖与配置：Spring Security 6.2 / jjwt 0.12.6 / MyBatis-Plus / Redis（db 1）；parent 统一管理 jjwt 版本
2. 建表脚本 `deploy/sql/02-auth-schema.sql`（7 张表 + 种子数据，`t_user` DROP 重建）
3. 7 个实体 + 7 个 Mapper（含"按用户查角色码/权限码"两条聚合查询）
4. `JwtTokenService`（双令牌 + `tenantId` + **`jti`**）、`TokenBlacklistService`（基于 `jti` 的失效名单）
5. `UserDetailsServiceImpl`（加载用户 + 角色 + 权限，禁用状态即时生效）
6. `AuthService`：登录 / 刷新轮换 / 登出撤销 + `AuthController` 四个接口
7. 安全层：`SecurityConfig`（无状态）、`JwtAuthenticationFilter`、`TenantContextFilter`（注入 + finally 清理）、401/403 两个处理器
8. 4 组单测（23 个用例）+ E2E 冒烟（9 步）

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 配置 | parent 加 jjwt 0.12.6 管理；link-auth 加 security/jjwt/mybatis-plus/redis/validation；`application.yml`（`link.auth.*`）+ `application-dev.example.yml` |
| 数据库 | `deploy/sql/02-auth-schema.sql`：`t_tenant` / `t_user`(重建) / `t_role` / `t_user_role` / `t_permission` / `t_role_permission` / `t_api_key`（P1 仅建表）+ 种子数据 |
| 代码 | 34 个源文件（7 实体 + 7 Mapper + 12 服务/安全/配置 + 控制器 + DTO/VO），包 `com.aaf.link.auth.*` |
| 测试 | 5 个测试类 23 个用例：`AuthLoginTest` / `AuthRefreshTokenTest` / `AuthDisabledAndBlacklistTest` / `AuthTenantIsolationTest` / `JwtTokenServiceTest` |
| 文档 | 本文件、`docs/test/V1-测试报告.md`、`docs/plans/P1-*.md`、技术点台账更新 |

---

## 3. 决策与理由

| # | 决策 | 理由（含"解决了上一版什么痛点"） |
|---|---|---|
| D1 | 包名规范化放 P1 第一步，独立提交 | 越晚改要动的文件越多；且它**还掉了 P0 的债**：common 与各服务包名前缀统一后，组件扫描边界问题一次性解决 |
| D2 | 保留 `com.shortlink` 的历史类名（如 `ShortLinkBloomFilter`） | 它是业务域名（"短链布隆过滤器"），不是模板痕迹，改名没有收益 |
| D3 | 认证框架沿用 Spring Security | JD 高频考点；参考实现已有完整链路可作蓝本 |
| D4 | **黑名单 key 用 `jti`** 而非整个令牌 | 令牌 200+ 字节，当 Redis key 很浪费；"JWT 撤销为什么用 jti"是高频面试题 |
| D5 | `/auth/refresh` 加入白名单 | 参考实现只放行 `/auth/login`，导致**刷新接口需要有效 Access Token 才能调用 —— 而刷新的前提正是它已过期**，这是参考实现的真实缺陷 |
| D6 | 白名单**单一来源**（配置 `link.auth.permit-all`） | 参考实现把白名单写在 `permitAll` 与过滤器 `shouldNotFilter` 两处，容易不一致导致越权/误拦 |
| D7 | 禁用用户在两处拦截：登录走 `DisabledException`，刷新走 `isEnabled()` 显式判断 | 覆盖两条路径；且不滥用异常语义 |
| D8 | 新增 `MethodSecurityExceptionHandler` 返回 403 | 修复"`@PreAuthorize` 拒绝被兜底异常处理器吞成 200"的安全漏洞（见踩坑 1） |
| D9 | auth 的 Redis key 用 `link:auth:` 前缀 + db 1 | 与 core、AAE 物理隔离 |
| D10 | 统计/配额相关能力不做 | P1 边界：只建 `t_api_key` 表，签名校验与配额限流放 P4 |
| D11 | 用户名全局唯一（`uk_username`） | **有意简化**：若支持跨租户同名用户，登录需携带租户标识。已在建表脚本注释中写明，留待后续 |

---

## 4. 踩坑

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | **只读账号访问 `@PreAuthorize("hasAuthority('user:manage')")` 接口返回"成功"** | `@PreAuthorize` 的 `AccessDeniedException` 抛在 **DispatcherServlet 内部**，比 Spring Security 的 `ExceptionTranslationFilter` 更靠里，**不会**被 `RestAccessDeniedHandler` 捕获；随后被 common 的兜底 `@ExceptionHandler(Exception.class)` 吃掉，返回 HTTP 200 + 业务错误码 → 前端误判成功（**事实上的鉴权失效**） | 新增 `MethodSecurityExceptionHandler` 专门处理 `AccessDeniedException` 返回 403；不放进 common，因为 common 依赖白名单里没有 spring-security |
| 2 | E2E 刷新接口返回 **401** | `/auth/refresh` 没进白名单。而刷新的前提恰恰是 Access Token 已过期，所以刷新接口必须免认证 | 加入 `link.auth.permit-all`（参考实现有同样缺陷） |
| 3 | 打好的 fat jar 启动报 `NoClassDefFoundError: com.aaf.link.common.result.Result` | 用 `mvn -pl link-auth package`（**没带 `-am`**）时，`link-common` 从**本地仓库**解析，拿到的是改名前的旧 jar，被 repackage 打进包里 | 带 `-am` 构建；更稳妥的是先 `mvn install` 刷新本地仓库（本项目已把"全量 install"作为收尾步骤） |
| 4 | 改包名后第一轮替换漏了 **16 处**配置键 | `@ConditionalOnProperty(prefix = "shortlink.mq")`、`${shortlink.mq.queue}`（`@RabbitListener` 占位符）、`${shortlink.stats-persist.*}`（`@Scheduled`）、异常信息里的 `shortlink.snowflake.*`、系统属性 `shortlink.benchmark` —— 这些**既不是 import 也不是包声明**，肉眼与"只换 package/import"的脚本都会漏 | 第二轮补充替换 `shortlink.` → `link.core.`；**关键教训：包名改完只跑编译不够，必须跑测试**（但这些坑连单测都抓不到，最终是 E2E 才暴露） |
| 5 | 包名统一后 common 的 Bean 全部扫不到 | `com.aaf.link.auth` 默认扫描范围不含 `com.aaf.link.common` | 启动类显式 `scanBasePackages = "com.aaf.link"`（P0 devlog 第 7 节预告的问题，此处还债） |
| 6 | 批量改名脚本可能破坏中文注释 | Windows 终端默认 GBK，普通读写会把 UTF-8 中文注释读坏 | 脚本内用 `.NET` 的 `UTF8Encoding($false)` 显式读写（UTF-8 无 BOM） |

---

## 5. 验证数据

| 节点 | 命令 | 结果 |
|---|---|---|
| 包名规范化后回归 | `mvn clean test` | core `198 / 0 / 0 / 1`（**与 P0 基线一字不差**） |
| auth 单测 | `mvn -pl link-auth -am test` | `23 / 0 / 0 / 0` |
| 全量构建 + 全部测试 | `mvn clean install` | 6 模块 BUILD SUCCESS；core 198、auth 23 |
| E2E 冒烟（真实起服务 + MySQL + Redis） | 9 步 | 见下表 |

**E2E 结果（9/9 通过）**

| 步骤 | 期望 | 实际 |
|---|---|---|
| 登录（tenantadmin） | 成功，租户 1 | `code=0, tenant=1` |
| 带令牌访问 `/auth/me` | 成功，租户上下文正确 | `code=0, tenant=1, roles=TENANT_ADMIN` |
| 无令牌访问受保护接口 | 401 | `401` |
| 只读账号访问需 `user:manage` 的接口 | 403 | `403`（修复踩坑 1 后） |
| 刷新令牌 | 成功 | `code=0` |
| 旧刷新令牌重放 | 被拒 | `A0401`（轮换防重放生效） |
| 登出 | 成功 | `code=0` |
| 登出后旧 Access Token | 401 | `401`（失效名单生效） |
| 禁用账号登录 | 被拒 | `A0403` |

---

## 6. 学到了什么（面试要点）

1. **包名规范化不是"替换字符串"**：真正的坑在**配置键**（`@ConditionalOnProperty` 前缀、`${}` 占位符、系统属性），它们既不在 import 里也不在包声明里，只跑编译发现不了。
2. **`@PreAuthorize` 的 403 要自己处理**：它比 Spring Security 的异常转换更靠里，会被全局兜底异常处理器吞掉变成 200 —— 这是"看起来能用、实际鉴权失效"的隐蔽漏洞，只有 E2E 能发现。
3. **刷新的白名单悖论**：刷新接口必须免认证，因为它的前提是 Access Token 已过期。参考实现没意识到这点。
4. **JWT 撤销用 `jti`**：黑名单 key 不用整个令牌，省 Redis 内存；TTL 取剩余有效期，到期自动清理，不需要定时任务。
5. **多租户越权的第一道闸**：`tenant_id` 只从令牌解析、服务端注入 `TenantContext`，`finally` 必须清理（线程池复用会串租户）。
6. **轮换防重放**：Redis 只保留最新 Refresh Token，刷新时比对，不等即拒 —— 旧令牌被偷也用不了。
7. **多模块 reactor 的 jar 陷阱**：`-pl` 不带 `-am` 时依赖可能取到本地仓库的过期版本；排查"编译能过、跑起来缺类"时优先怀疑这里。

---

## 7. 下一版预告（V2 / P2）

- 接入 Nacos：服务注册 + 配置中心（动态刷新），当前 `link.auth.*` / `link.core.*` 迁到 Nacos 配置。
- 打通服务间调用：OpenFeign（auth ↔ core），含超时、重试、日志级别。
- 前置清理项（本版遗留，见代码审查）：
  - core 的 `CacheKeyBuilder` 三个 Redis key 前缀仍是硬编码 `short-link:*`（与 `link:bloom:*`、`link:auth:*` 命名不一致）
  - `LinkCoreProperties.domain` 字段默认值仍是 `localhost:8080`（yml 已改 8082，但默认值未同步）
  - 用户名全局唯一（`uk_username`）的简化，若要做真多租户需要改造
