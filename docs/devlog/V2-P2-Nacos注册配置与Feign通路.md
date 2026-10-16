# V2（P2）前置清理 + Nacos 注册/配置中心 + Feign 通路

> 日期：2026-10-16 ｜ 版本：V2 ｜ 对应阶段：P2
> 一句话：先还掉 V1 遗留的 5 项技术债，再把 4 个服务接入 Nacos（注册 + 配置中心全量迁移 + 动态刷新），并打通 OpenFeign 服务间调用。

---

## 1. 做了什么

### 阶段零：前置技术债清理（V1 审查中已采纳的 5 项）
1. 先跑一遍存量测试作为"清理前基线"（core 198 / auth 28）
2. **M1**：core 的 Redis key 前缀硬编码 `short-link:*` → 统一为 `link:core:*`
3. **M2**：`LinkCoreProperties.domain` 默认值 `localhost:8080` → `localhost:8082`
4. **L4**：拆 `AuthProperties` → `JwtProperties`（`link.auth.jwt`）+ `AuthProperties`（只留白名单）
5. **L5**：`JwtAuthenticationFilter.currentUser()` → 新建 `security/SecurityUtils`
6. **L2**：`TenantContextFilter` 加"有主体但 tenantId 为空"的 WARN 告警
7. 清理中**又发现两处同类问题**并一并修掉（N2 + MQ 默认值，见第 3 节）
8. 跑回归确认无破坏（core 198 / auth 28，与基线一致）→ 独立提交

### 阶段一：Nacos 部署与注册
9. `deploy/nacos/docker-compose.yml` 拉起 Nacos 单机（`v2.4.2`，内嵌 Derby，**8848 + 9848/9849 三个端口**）
10. 4 个服务加 `spring-cloud-starter-alibaba-nacos-discovery`，配置注册中心地址 → 验证 4 个服务全部注册

### 阶段二：配置中心（全量迁 `link.*`）
11. core/auth 加 `spring-cloud-starter-alibaba-nacos-config` + `spring.config.import=optional:nacos:...`
12. `link.core.*` / `link.auth.*` 从本地 yml **全量迁出**，成为 `deploy/nacos/config/link-core.yaml`、`link-auth.yaml`（入库）
13. 写 `deploy/nacos/import-configs.ps1`：用 Nacos OpenAPI 一键发布 + **回读校验编码**
14. 本地 yml 只留：应用名 / profile / 端口 / Nacos 地址 / 框架级配置

### 阶段三：OpenFeign 通路（只打通，不做业务）
15. core 加 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer` + `@EnableFeignClients`
16. auth 加 `GET /internal/ping`（探活，**非业务功能**）；core 加 `GET /internal/feign-ping`（Feign 调 auth）
17. core 的 `/internal/ping` 额外**回显非敏感配置**，让"动态刷新"可观测
18. Feign 基础设施：超时（2s/5s，配置驱动）+ 重试（自定义 `Retryer`）+ 日志级别 + `ErrorDecoder`（把下游错误码转成本服务 `BizException`）
19. 动态刷新验证：Nacos 改 `cache.ttl-seconds` 1800→999，不重启生效，再还原
20. 容错验证：停掉 Nacos，服务仍能启动且可用（两条兜底路径都实测）

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 基础设施 | `deploy/nacos/docker-compose.yml`（Nacos 单机 + 三个端口 + 数据卷） |
| 配置即代码 | `deploy/nacos/config/link-core.yaml`、`link-auth.yaml`（入库，与 Nacos 上内容一致）+ `deploy/nacos/import-configs.ps1`（OpenAPI 发布 + UTF-8 回读校验） |
| 依赖 | 4 个服务加 nacos-discovery；core/auth 另加 nacos-config；core 另加 openfeign + loadbalancer |
| core 代码 | `client/AuthClient`（Feign 客户端）、`config/FeignConfig`（Retryer + ErrorDecoder）、`controller/InternalController`（探活 + 配置回显 + Feign 调用）、启动类 `@EnableFeignClients` |
| auth 代码 | `controller/InternalController`（探活） |
| 配置 | 4 个服务的 `application.yml` 瘦身 + 容错开关；core 的 Feign 超时/日志配置 |
| 测试 | 存量 226 个单测保持全绿（core 198 / auth 28）；E2E 7 项 + 容错 2 场景 |

---

## 3. 决策与理由（含"解决了上一版什么痛点"）

| # | 决策 | 理由 |
|---|---|---|
| D1 | **Feign 只打通链路，探活接口明确标注"非业务功能"** | auth 与 core 之间尚无真实业务调用需求（P3 网关→auth、P4 core→auth 才出现）。**不虚构**：宁可标注清楚，也不硬造一个假业务接口 |
| D2 | **配置全量迁 `link.*`，凭据留本地** | 迁业务配置才能动态调；`spring.datasource`/`spring.data.redis`（含口令）留 `application-dev.yml`（gitignore）。配置中心本身也要鉴权，学习项目把口令再挪一层收益低、风险高 |
| D3 | **Nacos 用 Docker 单机 + 内嵌 Derby** | 装一次用很久，Compose 一条命令起停；生产要换外置 MySQL + 开鉴权 |
| D4 | **容错模式三重保障** | ①`optional:` 让配置导入失败不致命 ②`discovery.fail-fast: false` 让注册失败不阻断启动（**踩坑 3 才发现默认是 true**）③**代码级白名单兜底**，防"配置中心挂了 → 白名单为空 → 登录被锁死" |
| D5 | **JWT 密钥不进配置中心** | 生产用环境变量、本地写 dev 配置。若把 `${LINK_AUTH_JWT_SECRET}` 放进 Nacos，配置中心的优先级可能盖掉本地 dev 密钥导致本地起不来（踩坑见 P1 的 S1） |
| D6 | **配置内容入库 + OpenAPI 一键发布** | 只在控制台手点一遍，换机/重建就全丢、也无法追溯。配置即代码才能复现与评审 |
| D7 | **区分"能动态刷新"与"不能"** | `@ConfigurationProperties` 字段可刷新（如 `cache.ttl-seconds`）；`@ConditionalOnProperty` 驱动的开关（`id-generator-type`、`mq.enabled`）在启动时决定 Bean 装配，**改了要重启**——但它们仍适合放配置中心（"改配置+重启"比"改代码+重新打包"轻） |
| D8 | **提交与文档命名规范** | 方案文档用 **P**（`docs/plans/P2-*`），版本/开发日志/测试报告/commit 用 **V**（`docs/devlog/V2-*`、`feat(V2)`）。V↔P 一一对应 |

---

## 4. 踩坑（现象 · 原因 · 解决）

### 坑 1：Nacos 里的中文配置解析失败，但日志写着 "success"（最危险的一类）
- **现象**：应用启动日志出现
  `[Nacos Config] Load config[dataId=link-auth.yaml, group=DEFAULT_GROUP] success`
  紧跟着 `ERROR ... Error getting properties from nacos: ... YAMLException: MalformedInputException: Input length = 1`；
  应用**照常启动**，但实际用的是本地默认配置（表现为白名单失效、`/swagger-ui.html` 返回 401）。
- **原因**：发布端编码错误。**PowerShell 5.1 的 `Invoke-WebRequest -Body <hashtable>` 用系统默认编码（中文 Windows = GBK）构造表单体**，把中文注释写成 GBK 字节存进了 Nacos；而 Java 客户端按 UTF-8 解码 → 非法字节。
- **解决**：改用 `[Uri]::EscapeDataString` 手工构造**纯 ASCII** 的表单体，并在发布后**回读校验字节是否为合法 UTF-8**（`UTF8Encoding($false, $true)` + 内容比对）。
- **教训**：**"假成功"比"明确失败"危险得多**。日志里的 success 只表示"HTTP 取到了内容"，不代表"内容能被解析"。

### 坑 2：Nacos 里明明是 UTF-8，客户端仍然解析失败
- **现象**：修好发布端后仍报 `MalformedInputException`。
- **原因**：**JDK 17 + 中文 Windows 的默认字符集是 GBK**。SCA 在客户端侧对配置字符串做 `.getBytes()`（无参 → 默认字符集）时又转成了 GBK 字节，而 SnakeYAML 按 UTF-8 解析。
- **解决**：JVM 启动参数加 `-Dfile.encoding=UTF-8`。加完后日志变为
  `Load config ... success` + `Listening config: dataId=link-auth.yaml`（监听注册成功），且**不再有 Error**。
- **补充**：JDK 18+ 的 `file.encoding` 默认已是 UTF-8，此问题会自然消失。

### 坑 3：`optional:nacos:` 并不能保证"Nacos 挂了服务还能起"
- **现象**：停掉 Nacos 后启动服务，端口一度可连但随即关闭；日志出现
  `NacosException: Client not connected, current status:STARTING`（来自 `NacosServiceRegistry.register`），随后 `NotifyCenter Start destroying`（上下文关闭）。
- **原因**：**SCA 的 `spring.cloud.nacos.discovery.fail-fast` 默认为 `true`**，注册失败会 rethrow，直接把启动打断。`optional:` 只管"配置导入"，管不到"服务注册"——**两者是一对，容易只做一半**。
- **解决**：4 个服务显式 `spring.cloud.nacos.discovery.fail-fast: false`。

### 坑 4：Docker Hub 直连不通
- **现象**：`docker compose up` 报 `failed to resolve reference ... dialing registry-1.docker.io:443 ... failed to respond`。
- **解决**：探测国内镜像源（`docker.m.daocloud.io` 可达），先 `docker pull docker.m.daocloud.io/nacos/nacos-server:v2.4.2`，再 `docker tag` 回标准名，compose 文件不用改。
- **补充**：镜像版本与客户端对齐——SCA 2023.0.3.2 带的是 `nacos-client 2.4.2`，服务端也用 `v2.4.2`。

### 坑 5：`.ps1` 脚本里的中文字符串会被 PowerShell 5.1 读坏
- **现象**：脚本报 `Unexpected token ')'`、`The string is missing the terminator`。
- **原因**：无 BOM 的 `.ps1` 在 PowerShell 5.1 下按 **ANSI（GBK）** 读取，中文字符串字面量被解码成乱码，把引号/括号"吃掉"。（中文注释无害，因为注释被丢掉。）
- **解决**：临时脚本的输出一律用 ASCII；需要中文时用 `write_to_file` 写 UTF-8 且只在注释里用中文。

### 坑 6：Nacos 客户端的本地快照会"伪装"成兜底生效
- **现象**：停掉 Nacos 后启动 auth，`/swagger-ui.html` 仍返回 302（完整白名单生效），看起来像"代码兜底白名单起了作用"。
- **原因**：**Nacos 客户端会把配置缓存到本地**（`~/nacos/config`），中心不可用时回退到快照。
- **解决/教训**：删掉本地快照后重测，才看到真实行为（`/auth/login` 仍可用、`/swagger-ui.html` → 401），从而**分别验证两条兜底路径**。做容错验证时必须意识到快照的存在，否则会把结论归错因。

---

## 5. 验证数据

### 5.1 前置清理（阶段零）
| 节点 | 结果 |
|---|---|
| 清理前基线 | core `198 / 0 / 0 / 1`、auth `28 / 0 / 0 / 0` |
| 清理后回归 | core `198 / 0 / 0 / 1`、auth `28 / 0 / 0 / 0`（**与基线一致**） |
| 缓存 key 实测 | 日志 `key=link:core:link:localhost:8082:abc123`（前缀与 domain 双双生效） |

### 5.2 注册与配置中心
| 检查项 | 结果 |
|---|---|
| Nacos 就绪 | `readiness=OK`，控制台 HTTP 200，`started successfully in stand alone mode` |
| 配置发布 | `link-auth.yaml`(1259 字符) + `link-core.yaml`(3834 字符)，**回读 UTF-8 校验通过、内容一致** |
| 服务注册 | `{"count":4,"doms":["link-gateway","link-auth","link-core","link-stats"]}` |
| core 配置来源 | `Load config[dataId=link-core.yaml] success`、无 `Error getting properties`、`Listening config` 已注册 |

### 5.3 Feign 通路
| 检查项 | 结果 |
|---|---|
| core 探活 | `/internal/ping` → `ttl=1800 domain=localhost:8082 idGen=auto` |
| Feign 调用 | `/internal/feign-ping` → caller `link-core:8082`，callee `{"service":"link-auth","instance":"link-auth:8081",...}`（**按服务名调用成功**） |

### 5.4 动态刷新（可观测）
| 步骤 | 结果 |
|---|---|
| 改前 | `cache.ttl-seconds = 1800` |
| Nacos 发布 999 后（不重启） | **`999`** ✅ |
| 还原 1800 后 | **`1800`** ✅ |

### 5.5 容错（两条路径分别验证）
| 场景 | 结果 |
|---|---|
| Nacos 停机启动 auth/core | ✅ 两个服务均输出 `Started ...Application in` |
| **有本地快照**（删快照前） | `/swagger-ui.html` → 302（完整白名单，走**客户端快照兜底**） |
| **无本地快照**（删 `~/nacos` 后） | 启动 ✅；`/auth/login` → `code=0`（**代码兜底保住登录**）；`/swagger-ui.html` → 401、`/auth/me` → 401（确认完整白名单未生效、受保护接口仍被拦） |

### 5.6 全量
`mvn clean install` → 6 模块 BUILD SUCCESS；单测合计 **230 个全绿**
（core 202 = 基线 198 + 复审新增 `FeignConfigTest` 4；auth 28）。

### 5.7 复审修复（P2-3 / P2-4 / P2-5 / P2-6）
| 项 | 改法 | 验证 |
|---|---|---|
| P2-3 + P2-4 | 抽出 `ClassifiedErrorDecoder(objectMapper, retryOnServerError)`；全局 `FeignConfig` **不注册 Retryer** 且传 `false`（安全默认），只有 `AuthClient` 用 `@FeignClient(configuration = IdempotentReadFeignConfig.class)` 开启重试 | 单测 4 用例（两种语义）+ 运行时 `/internal/feign-ping` 复验通过 |
| P2-5 | `/internal/ping` 的配置回显加 `link.core.internal.expose-config` 开关（默认 `false`） | 开启后回显正常、Feign 仍通 |
| P2-6 | `docs/03` 新增「8.7 配置归属约定」+ 三条铁律 | 文档 |

---

## 6. 学到了什么（面试要点）

1. **"能启动"和"能用"是两件事**：容错设计要分两层——`optional:` 管配置导入、`fail-fast:false` 管服务注册，**少一层就是"能启动但不可用"**。更进一步，配置中心挂了还要靠代码兜底保住关键入口（登录），否则服务活着等于死了。
2. **假成功最危险**：日志 `Load config ... success` 只是"取到了内容"，解析失败会静默回退默认值。所以发布配置后要**回读校验**，而不是相信发布接口的返回。
3. **编码问题两端都会出**：发布端（PowerShell 用 ANSI 拼表单）与消费端（JDK 17 默认 GBK）都能把 UTF-8 配置弄坏。
4. **默认值必须实测**：`discovery.fail-fast` 默认 `true` 是我方案里的错误假设——文档没写就得实测。
5. **区分"可动态刷新"与"需重启"**：`@ConfigurationProperties` 可刷新；`@ConditionalOnProperty` 决定 Bean 装配，改配置不重启不生效。
6. **自举顺序**：Nacos 地址和 `spring.config.import` 必须在本地可解析——否则"要拿配置得先连中心，要连中心得先有配置"。
7. **配置即代码**：配置入库 + OpenAPI 发布 + 回读校验，换来"可复现、可评审、可追溯"。

---

## 7. 下一版预告（V3 / P3）

- **网关路由与统一鉴权**：`link-gateway` 配置路由（`/auth/**` → link-auth、`/api/**` → link-core），在网关收口鉴权与限流。
- **必须收回的技术债**：P2 把 `/internal/**` 临时加进了 auth 白名单——**P3 网关上线后内部接口不应对外暴露**，应改为内网隔离或内部鉴权。
- **Sentinel**：接口级限流与熔断降级。
