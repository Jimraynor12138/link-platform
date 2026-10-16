# P1（V1）方案：link-auth 认证链路与租户模型

> 本文是 P1 阶段的方案文档（入库版，便于阅读与复盘）。
> 对应版本：V1 ｜ 预计 2~3 天 ｜ 前置版本：V0（P0 多模块骨架与核心迁移，已完成）

---

## 1. 本阶段目标

让 `link-auth` 从"空骨架"变成真正的认证服务中心，并把全项目的包名与配置前缀规范化到统一命名空间。
**原有 198 个单测必须保持全绿**，作为"规范化没有破坏任何东西"的硬证据。

---

## 2. 范围（已确认）

### 2.1 做

- **包名与配置前缀规范化**：`com.shortlink.*` → `com.aaf.link.*`；配置前缀 `shortlink.*` → `link.core.*`
- **认证链路**：登录 / 刷新 / 登出三个接口；双 Token；刷新轮换；登出即撤销
- **租户模型**：新增租户表，用户归属租户，平台/租户两级权限，越权防护
- **建表脚本 + 种子数据**：平台超管、演示租户、租户管理员、只读账号
- **4 组单测**：登录 / 刷新轮换 / 禁用与黑名单 / 租户越权
- **E2E 冒烟**：登录 → 带令牌访问 → 刷新 → 登出 → 旧令牌被拒

### 2.2 不做（留到后续版本）

用户与角色的管理类接口（→P6）、API Key 签名校验与配额（→P4，本阶段只建表）、Nacos 注册（→P2）、网关与 Sentinel（→P3）、前端（→P6）、统计拆分（→P5）。

---

## 3. 阶段一：包名与配置前缀规范化（零行为变更，独立提交）

**目标形态**：
- `link-common` → `com.aaf.link.common.*`
- `link-core` → `com.aaf.link.core.*`
- 配置前缀 `shortlink.*` → `link.core.*`（原 `shortlink:` 下所有配置整体缩进到 `link.core:` 下）
- 类名规范化：`ShortLinkApplication` → `LinkCoreApplication`、`ShortLinkProperties` → `LinkCoreProperties`

**为什么放第一步**：包名统一后，"公共模块与各服务的组件扫描边界"问题一次性解决（P0 开发日志第 7 节已预告）；越晚改，要动的文件越多。

**三处硬编码必改点**（漏改即失败）：

1. `MybatisPlusConfig` 的 `@MapperScan("com.shortlink.dao")`
2. `ShortLinkProperties` 的 `@ConfigurationProperties(prefix = "shortlink")`
3. `BeanWiringTest` 的 `withPropertyValues("shortlink.id-generator-type=...")`

**顺带解决扫描边界**：`LinkCoreApplication` 与 `AuthApplication` 改用
`@SpringBootApplication(scanBasePackages = "com.aaf.link")`，否则扫不到 `com.aaf.link.common` 下的全局异常处理器与租户上下文。

**验收**：`mvn clean test` 必须仍是 `Tests run: 198, Failures: 0, Errors: 0, Skipped: 1`（与 P0 基线一字不差）。

---

## 4. 阶段二：link-auth 认证与租户模型

**原则：机制移植 + 代码重写。** 从 jimraynor 移植"认证机制"，代码全部按业务重写。

### 4.1 移植的机制

| 机制 | 实现要点 |
|---|---|
| 双令牌 | 同一密钥签发，`type` claim 区分 access / refresh；claims 带 `userId`、`tenantId`、`jti` |
| 刷新轮换防重放 | refresh 存 Redis（`link:auth:refresh-token:{userId}`）；刷新时与 Redis 当前值比对，**不等即 401**（旧令牌自动失效），成功后覆盖 |
| 刷新时重载用户 | 重新加载用户 + 权限；禁用用户抛 `DisabledException` 无法续期；权限变更即时生效 |
| 登出即撤销 | 取剩余有效期 → 写 Redis 失效名单（key 用 **`jti`**）→ 删 refresh → 清空安全上下文 |
| 401 / 403 分离 | 未认证走 `AuthenticationEntryPoint`，已认证无权限走 `AccessDeniedHandler` |
| 无状态链路 | CSRF 关闭、`SessionCreationPolicy.STATELESS`、JWT 过滤器置于 `UsernamePasswordAuthenticationFilter` 之前、`@EnableMethodSecurity` |

### 4.2 与参考实现刻意不同的 5 处（"重写"的价值，面试可讲）

| 参考实现的问题 | 本版本改法 | 理由 |
|---|---|---|
| 白名单在两处重复（`permitAll` + 过滤器 `shouldNotFilter`） | 收敛为单一声明 | 避免两处不一致导致越权或误拦 |
| 黑名单 key = 整个令牌（200+ 字节） | **JWT 加 `jti`，key 用 `jti`** | 省 Redis 内存；"JWT 撤销为什么用 jti"是高频面试题 |
| 每个请求都查库加载用户 | 保留，但注释与开发日志写明取舍 | 明确"新鲜度 vs DB 负载" |
| `System.out.println` + 每请求日志 + 打印令牌 | 全部移除，**绝不打印令牌明文** | 生产可用性 |
| 注释含教程/AI 痕迹与未解决 TODO | 重写为业务化注释，并把 2 个 TODO 真正解决 | 去模板痕迹 |

### 4.3 多租户与越权防护（本阶段核心卖点）

- **两级模型**：平台级（`tenant_id = 0`，复用 `CommonConstants.PLATFORM_TENANT_ID`，可跨租户）与租户级（`tenant_id > 0`，仅本租户）
- **角色用权限码**：平台管理员（`tenant:manage`/`user:manage`）、租户管理员（`user:manage`/`link:manage`）、只读（`link:read`）
- **租户标识唯一可信来源是令牌**：过滤器解析 `tenantId` claim → 注入 `TenantContext`（ThreadLocal）→ `finally` 中 `clear()`（线程池复用会串租户）→ 业务代码从上下文取值，**永不接受前端传入的 tenant_id**

---

## 5. 数据模型

建在 `link_platform` 库（与 core 共享库共享表），脚本 `deploy/sql/02-auth-schema.sql`。

| 表 | 关键字段 |
|---|---|
| `t_tenant` | id, name, status, quota_per_day, create_time, update_time |
| `t_user`（**DROP 重建**） | id, **tenant_id**, username(唯一), password(BCrypt), nickname, status, del_flag, create_time, update_time |
| `t_role` | id, **tenant_id**, code, name, remark |
| `t_user_role` | id, user_id, role_id |
| `t_permission` | id, code, name |
| `t_role_permission` | id, role_id, permission_id |
| `t_api_key`（**只建表**） | id, tenant_id, access_key, secret, quota, expire_at, status, create_time |

> `t_user` 采用 DROP + 重建：`link_platform` 库里已有 AAE 的极简 `t_user`，但 core 代码从未引用它、库内无数据，重建比 ALTER 干净。

---

## 6. 关键契约

`LoginUser`（所有链路的公共契约，实现 `UserDetails`）：

```java
private Long id;
private Long tenantId;              // 0 = 平台级，>0 = 租户级
private String username;
private String password;
private String nickname;
private List<String> roles;         // 角色码，前端展示用
private List<String> permissions;   // 权限码，如 user:manage
private Collection<? extends GrantedAuthority> authorities;  // 由 permissions 构建
```

`JwtTokenService` 对外契约：

```java
String generateAccessToken(LoginUser user);
String generateRefreshToken(LoginUser user);
boolean validateAccessToken(String token);
boolean validateRefreshToken(String token);
String getUsername(String token);
Long getUserId(String token);
Long getTenantId(String token);
String getTokenId(String token);                 // jti，失效名单 key
long getRemainingExpirationMillis(String token); // 黑名单 TTL
long getRefreshExpirationMillis();
String resolveToken(HttpServletRequest request);
```

---

## 7. 技术栈

| 类别 | 选型 | 说明 |
|---|---|---|
| 基础框架 | Spring Boot **3.2.5**（沿用，不升级） | 保证 P0 的 198 单测基线不被扰动 |
| 安全框架 | Spring Security 6.2（Boot 3.2.5 自带） | JD 高频考点 |
| 令牌 | jjwt 0.12.6（api 编译期 / impl、jackson 运行期） | 版本在 parent 的 dependencyManagement 统一管理 |
| 持久层 | MyBatis-Plus 3.5.7 + MySQL 8（`link_platform`） | **不要照搬参考实现的 `mybatis-plus-jsqlparser`**（3.5.9+ 才拆出） |
| 缓存 | Redis **db 1** | 刷新令牌、失效名单；与 AAE 的 db 0 物理隔离 |
| 测试 | JUnit 5 + Mockito（纯单测） | 离线可跑、秒级反馈，与 core 一致 |

> 版本差异提醒：参考实现（jimraynor）是 Boot 3.5.14 / MyBatis-Plus 3.5.14 / springdoc 2.8.13，本仓库是 3.2.5 / 3.5.7 / 2.3.0，Security DSL 需逐项核对。

---

## 8. 验收标准

| 项 | 标准 |
|---|---|
| 包名规范化回归 | `mvn clean test` → `Tests run: 198, Failures: 0, Errors: 0, Skipped: 1` |
| auth 单测 | 4 组全绿（登录 / 刷新轮换 / 禁用与黑名单 / 租户越权） |
| E2E 冒烟 | 登录成功 → 带令牌访问成功 → 刷新成功 → 登出后旧令牌被拒 |
| 全量构建 | `mvn clean install -DskipTests` 六模块 BUILD SUCCESS |
| 文档 | `docs/devlog/V1-P1-*.md` 七小节 + 索引/路线图/README 更新 |
| DoD 四件套 | 编译通过 + 测试数字 + devlog 更新 + git commit |

---

## 9. 执行步骤清单

**阶段一：包名规范化**
1. 盘点 `com.shortlink` 全量引用与三处硬编码点，产出待改清单
2. 重命名 common 与 core 包 → `com.aaf.link.*`，同步 `@MapperScan`、启动类名、扫描范围
3. 配置前缀 `shortlink.*` → `link.core.*`，同步 yml、配置类、BeanWiringTest 配置键
4. 校验无残留引用 + 跑测试确认 198/0/0/1

**阶段二：auth 认证与租户模型**

5. link-auth 引入 security / jjwt / mybatis-plus / redis / validation，parent 加 jjwt 版本管理

> 注：步骤 6~16 与本文件正文对应，此处略（详见下方执行清单）。

---

## 10. 已知风险与坑

1. **包名批量替换必须全量核对**：6 个公共类 + 68 个 core 主类 + 28 个测试类 + 配置；**只跑编译不够，必须跑测试**（配置键写错只有测试能发现）
2. **扫描范围**：包名统一后必须显式 `scanBasePackages = "com.aaf.link"`，否则全局异常处理器与租户上下文 Bean 全部失效
3. **版本降级适配**：Security 6.5 → 6.2；不要照搬 `mybatis-plus-jsqlparser`
4. **`t_user` 冲突**：DROP + 重建；执行脚本前先 `SHOW TABLES` 确认现状
5. **Redis 必须落 db 1**：绝不能落 db 0（与 AAE 冲突）
6. **真实凭据不入库**：auth 的 `application-dev.yml` 沿用 core 规则被 .gitignore 排除，另提供 example 模板
7. **不打印令牌**：日志只记事件与用户标识
8. **不可虚构**：未实现的能力不能在文档里写成"已完成"

---

## 11. 后续预留

- **P4**：API Key 签名校验 + 配额限流（本阶段只建 `t_api_key` 表）
- **P5**：统计链路 MQ 抽象，RabbitMQ ↔ Kafka 可切换（**不引入 RocketMQ**）
- **P7**：Docker Compose 编排 + 压测对比；基础设施（Kafka/Nacos/Sentinel）容器化，业务服务本地开发、最后统一打镜像
