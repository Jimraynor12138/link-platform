# P2（V2）方案：前置技术债清理 + Nacos 注册/配置中心 + Feign 通路

> 本文是 P2 阶段的方案文档（入库版，便于阅读与复盘）。
> 对应版本：V2 ｜ 前置版本：V1（P1 link-auth 认证与租户模型，已完成）

---

## 1. 本阶段目标

1. **先清债**：把 V1 代码审查中已采纳的 5 项技术债清掉，为后续演进腾出干净地基。
2. **接注册中心**：用 Docker 拉起 Nacos，4 个服务注册上去。
3. **接配置中心**：把 `link.core.*` / `link.auth.*` 业务配置全量迁到 Nacos，支持动态刷新。
4. **打通服务调用**：建立 OpenFeign 通路（超时/重试/日志/错误解码），用探活接口验证连通。

**全程采用容错模式**：Nacos 未启动时服务仍能拉起，日常开发不必"必须先开 Docker"。

---

## 2. 命名规范（本阶段起生效）

| 用途 | 用什么 | 例子 |
|---|---|---|
| 计划 / 阶段（方案文档） | **P** | `docs/plans/P2-*.md` |
| 版本（devlog / 测试报告 / 路线图 / **git commit**） | **V** | `docs/devlog/V2-P2-*.md`、`docs/test/V2-*.md`、`feat(V2): ...` |
| 对应关系 | V0=P0、V1=P1 …… 一一对应 | 文档统一写 `V2（P2）` |

> 仓库历史中 P0/P1 的提交保持原样（不改写历史）；V↔P 一一对应，无歧义。

---

## 3. 阶段零：前置清理（V1 遗留技术债）

**为什么独立成阶段**：M1/M2 会改动缓存 key 与测试观察值。若与"接 Nacos"混在一起，一旦回归变红无法二分定位——延续项目一贯的"一次只引入一个变量"原则。

| 编号 | 问题 | 改法 | 风险 |
|---|---|---|---|
| **M1** | core 的 Redis key 前缀仍是硬编码 `short-link:*`，与 `link:bloom:*`、`link:auth:*` 命名不一致 | `CacheKeyBuilder` 三个常量统一为 `link:core:link:` / `link:core:stats:` / `link:core:lock:rebuild:` | 必须全量 grep `short-link:` 同步测试断言；Redis 已是 db 1，此项是"命名整洁"而非"隔离必需" |
| **M2** | `LinkCoreProperties.domain` 默认值仍是 `localhost:8080`（yml 已是 8082） | 字段初始值改为 `localhost:8082` | 测试中 `new LinkCoreProperties()` 的 domain 会变，缓存 key 内容随之变化 |
| **L4** | `AuthProperties` 同时承载 `jwt` 与 `permitAll`，职责略杂 | 拆为 `JwtProperties`（`link.auth.jwt`，含启动期密钥校验）+ `AuthProperties`（只留白名单） | **yml 结构不用改**（`link.auth.jwt.*` 本就是嵌套写法） |
| **L5** | `JwtAuthenticationFilter.currentUser()` 工具方法位置不当 | 新建 `com.aaf.link.auth.security.SecurityUtils` 并迁移 | 必须放 auth 模块（common 没有 spring-security 依赖） |
| **L2** | `TenantContextFilter` 依赖过滤器顺序，顺序错会**静默失效** | 有认证主体但 tenantId 为空时打 **WARN**（把静默变可见）+ 强化注释 | 只加告警，不改两过滤器架构 |

**搁置不动**：M3（每请求查库，等 P7 压测数据）、M4（用户名全局唯一，等 P6）、L1（`@Valid` 补单测）、L3（Mapper 加 `@Mapper`）、L6（E2E 自动化）。

---

## 4. 阶段一：Nacos 部署与注册

### 4.1 部署（Docker 单机 + 内嵌 Derby）

- 镜像 `nacos/nacos-server:v2.3.2`，`MODE=standalone`
- **端口必须暴露：8848（HTTP/控制台）+ 9848/9849（gRPC）** —— 只开 8848 会出现"控制台能开、客户端连不上"
- 控制台 `http://localhost:8848/nacos`（默认 nacos/nacos）
- 编排文件：`deploy/nacos/docker-compose.yml`
- 文档注明：生产要换外置 MySQL（`nacos_config` 库）并开启鉴权

### 4.2 注册

- 依赖 `spring-cloud-starter-alibaba-nacos-discovery`（版本由 SCA BOM 管理）
- `link-gateway` / `link-auth` / `link-core` / `link-stats` 全部注册，注册名 = `spring.application.name`

---

## 5. 阶段二：配置中心（全量迁 `link.*`）

- 依赖 `spring-cloud-starter-alibaba-nacos-config`；**Boot 3.x 用 `spring.config.import`，不用 `bootstrap.yml`**
- DataId：`link-core.yaml`（`link.core.*`）、`link-auth.yaml`（`link.auth.*`）
- 本地 yml 只留：`spring.application.name`、`server.port`、`spring.profiles.active`、Nacos `server-addr`、`spring.config.import` 本身
- **自举顺序**：`spring.config.import` 与 `spring.cloud.nacos.server-addr` 必须在本地可解析，不能放 Nacos
- **容错**：`spring.config.import=optional:nacos:...`（导入失败不致命），配合 `spring.cloud.nacos.config.import-check.enabled=false`
- **凭据不进配置中心**：`spring.datasource.*`（含密码）、`spring.data.redis.*` 留本地 `application-dev.yml`（已 gitignore）
- **配置可复现**：配置内容入库 `deploy/nacos/config/*.yaml` + Nacos OpenAPI 一键导入脚本
- **动态刷新可观测**：改一个能看出效果的配置（如 `link.core.cache.ttl-seconds`）并发布，确认应用侧行为变化

---

## 6. 阶段三：OpenFeign 通路（只打通，不做业务）

- 依赖 `spring-cloud-starter-openfeign` + **`spring-cloud-starter-loadbalancer`（必须显式引入）**
- 启动类 `@EnableFeignClients`；方向 `link-core` → `link-auth`
- 探活接口（**非业务功能，仅用于验证链路**）：auth `GET /internal/ping`；core `GET /internal/feign-ping`
- **必须处理的坑**：auth 是 `anyRequest().authenticated()`，`/internal/**` 不加白名单会被拦成 401 —— 本阶段临时放行，**P3 网关上线后应改内网隔离或内部鉴权**
- Feign 基础设施：超时、重试（默认 `NEVER_RETRY`，只对幂等读开启）、日志级别（`FULL` 会打印请求体）、`ErrorDecoder`
- 单测（纯 Mockito）：覆盖 正常返回 / 下游 4xx / 下游 5xx / 超时 四分支

---

## 7. 验收标准

| 项 | 标准 |
|---|---|
| 阶段零后回归 | core `198/0/0/1`、auth `28/0/0/0`（与 V1 基线一致） |
| 全量构建 | `mvn clean install -DskipTests` 6 模块 BUILD SUCCESS |
| Nacos 注册 | 控制台/OpenAPI 可见 4 个服务实例 |
| 配置中心 | 启动体现从 Nacos 拉配置；改配置发布后**应用侧可观测地生效** |
| Feign 通路 | `curl http://localhost:8082/internal/feign-ping` 成功，结果能看出由 `link-auth` 返回 |
| 容错 | 停掉 Nacos 后服务仍能启动 |
| 文档与记录 | 方案、devlog（含"前置清理"专节）、测试报告、**技术债台账**、索引/路线图/README |

---

## 8. 坑清单

1. **Nacos 2.x 必须暴露 9848/9849 gRPC 端口**
2. **`spring.config.import` 与 Nacos 地址必须写在本地 yml**（自举顺序）
3. **`/internal/**` 不加白名单会被 auth 的 Spring Security 拦成 401**
4. **`spring-cloud-starter-loadbalancer` 必须显式引入**
5. **不用 `bootstrap.yml`**
6. **配置迁移后必须重跑存量 226 个单测**（理论不受影响，但要证明）
7. **Feign 日志 `FULL` 会打印请求体**，注意敏感信息
8. **重试要与超时、幂等性一起考虑**（读操作才重试）
9. Windows GBK 终端中文可能乱码，必要时 `chcp 65001` 或 `-Dfile.encoding=UTF-8`

---

## 9. 文档交付

- 本方案（`docs/plans/P2-Nacos与Feign通路.md`）
- `docs/devlog/V2-P2-*.md`（七小节，含"V2 前置清理"专节）
- `docs/test/V2-测试报告.md`
- `docs/03-技术点与面试素材.md` 新增**「技术债台账」**（登记 S1、M1~M4、L1~L6 及后续审查项的状态与归属版本）
- 更新 `docs/02` 索引、`docs/01` 路线图、`README.md`
