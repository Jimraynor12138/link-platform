# V7（P7）Docker Compose 编排 + 压测对比

> 日期：2026-10-17 ｜ 版本：V7 ｜ 对应阶段：P7
> 一句话：把"本地多进程 + 手工起中间件"升级成"一条命令起全栈"，只暴露 80/8080，并用真实压测量化"多一跳""同步 vs 异步""Rabbit vs Kafka"三组代价。

---

## 1. 做了什么

1. **镜像**：通用多阶段 `Dockerfile.service`（layered jar，三个服务共用）+ `link-web` 的 Nginx 镜像
2. **编排**：`deploy/docker/docker-compose.yml` 一次性拉起 MySQL/Redis/RabbitMQ/Nacos/Kafka + auth/core/gateway/web
3. **网络收口**：`link-auth` / `link-core` **不映射宿主机端口**，宿主机直连 8081/8082 被拒（修 P3-1/P2-2）
4. **前端托管**：Nginx 托管 `dist` + SPA history 回退 + `/auth` `/api` `/open` 反代（修 P6-3）
5. **配置**：全部用环境变量注入（12-factor），Nacos 配置单独发布；新增 `import-configs.sh`（修 P2-7）
6. **健康检查**：三个服务加 actuator（只暴露 health/info），compose 用 `bash /dev/tcp` 探活
7. **压测**：`bench/loadtest.mjs`（零依赖）+ 三组对比数据

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 构建 | `deploy/docker/Dockerfile.service`、`deploy/docker/link-web/Dockerfile`、`.dockerignore` |
| 编排 | `deploy/docker/docker-compose.yml`、`docker-compose.bench.yml`（临时暴露 8082）、`docker-compose.bench-core.yml`（压测参数）、`.env.example` |
| 前端 | `deploy/docker/nginx/default.conf` |
| 脚本 | `bench/loadtest.mjs`、`bench/README.md`、`deploy/nacos/import-configs.sh` |
| 代码 | 三模块加 `spring-boot-starter-actuator` + `management.*`；`VisitStatsConsumer` 占位符补默认值 |

---

## 3. 决策与理由

| # | 决策 | 理由 |
|---|---|---|
| D1 | 宿主机打包、容器只拷 jar | 容器内跑 Maven 会重复下载依赖，且当前网络不稳；分层 jar 仍保留"改代码只重建最后一层"的收益 |
| D2 | 复用本机已有镜像（mysql:8.4 / redis:8 / temurin:17-jre / nginx:stable-alpine） | 只新拉了一个 rabbitmq，避免 6 个镜像的下载风险 |
| D3 | 前端改成"宿主机构建 dist + Nginx 只拷产物" | 省掉 node 镜像与一次 `npm ci`，与后端策略一致 |
| D4 | 只暴露 80/8080，中间件不映射 | 收口 P3-1 的同时避免与本地 MySQL/Redis/RabbitMQ 抢端口 |
| D5 | 压测开关用命令行参数而非环境变量 | 见坑 3（cmd `set` 尾随空格） |
| D6 | 压测脚本零依赖（全局 fetch） | 环境下载能力不稳定，自写脚本最可靠 |

---

## 4. 踩坑（现象 · 原因 · 解决）

1. **`application-dev.yml` 会被打进 jar**：`.gitignore` 只挡 git，不挡 Maven。镜像里会带上本地数据库密码。
   → Dockerfile 解包后 `rm -f application/BOOT-INF/classes/application-dev*.yml`。
2. **`@RabbitListener(queues = "${link.core.mq.queue}")` 无默认值**：Nacos 配置还没发布时，占位符解析失败，core 直接启动失败——这破坏了 `optional:nacos` 的"Nacos 不可用也能启动"承诺（**真 bug，已修**）。
   → 补默认值 `${link.core.mq.queue:link.visit.queue}`。
3. **cmd 的 `set VAR=value && cmd` 会把尾随空格写进变量**：`set LINK_CORE_MQ_ENABLED=false && ...` 实际值变成 `"false "`，`@ConditionalOnProperty` 既不等于 `true` 也不等于 `false` → **两个 `VisitRecorder` 都没装配**，应用启动失败、容器无限重启。
   → 用 `set "VAR=value"`（带引号）。**这是本次排查耗时最久的坑，也是最容易误判成"框架不认环境变量"的坑。**
4. **RabbitMQ 的 `guest` 只允许回环**：跨容器连接 `ACCESS_REFUSED`。
   → `RABBITMQ_DEFAULT_USER/PASS` 建非 guest 账号。
5. **Kafka 双 listener**：容器内必须用 `link-kafka:19092`（advertised 的 PLAINTEXT），宿主机才是 `localhost:9092`。
6. **Nacos 配置发布顺序**：core 启动前若配置未发布，会走代码默认值（修了坑 2 后可启动）；发布后需重启才生效（`spring.config.import` 是启动期加载）。
7. **`link.core.domain` 必须改走网关**：否则生成的短链是 `localhost:8082`，收口后打不开。
8. **单进程压测客户端会饱和**：c=50 时直连/网关都卡在 ~2500 QPS，谁快谁慢是噪声；**必须降并发测延迟**（c=10）才有信号。
9. **容器初始化中文双重编码**：官方 mysql 镜像的 `docker-entrypoint.sh` 用 `mysql` CLI 执行 `deploy/sql/*.sql`，客户端字符集 `auto` 跟随容器 locale（未设 `LANG` 时落到 latin1）→ 种子中文（`默认分组`、`演示租户`…）被按 latin1 解释后再转存 utf8mb4，读出来是 `é»˜è®¤åˆ†ç»„`。
   → 四个 SQL 顶部加 `SET NAMES utf8mb4`（与客户端 locale 解耦）+ compose 给 MySQL 加 `LANG=C.UTF-8`（双保险）；存量数据用 `deploy/sql/repair/01-fix-mojibake.sql` 原地修复。**只看服务端 `--character-set-server` 不够，客户端字符集是独立的一环。**

   **修复脚本第一版还踩了个坑**：用 `LENGTH = 2 × CHAR_LENGTH` 识别乱码行，结果 **0 行命中**。因为 MySQL 的 `latin1` 实际是 **cp1252**，`0x98/0x86/0x84` 会被映射成 `˜/†/„` 这些 **3 字节**字符——乱码串是"2 字节为主 + 少量 3 字节"，长度比不成立（实测 `26 ≠ 2×12`）。改用"UTF-8 首字节是 `C2/C3`"识别（正常中文首字节是 `E4–E9`、ASCII 是 `41–7A`），一次命中 9 行。
   **教训**：处理编码问题时，"猜长度"不如"看首字节"；而且 `latin1` 这个名词在不同系统里含义不同（MySQL 的是 cp1252，不是 ISO-8859-1）。

---

## 5. 验证数据

### 5.1 栈可用性

| 项 | 结果 |
|---|---|
| 容器 | 9 个容器全部 healthy（web 无 healthcheck） |
| 登录（经 Nginx→网关→auth） | `200/0` |
| 建链 + 跳转 | `code=1`，`GET :8080/1` → **302** `https://example.com/docker` |
| 看板 | `overview` pv=1/uv=1；`channels` → `docker/pv=1` |
| 静态托管 | `GET :80/` → 200，返回含 `#app` 的 index.html 与 hash 资源 |
| **网络收口** | `localhost:8081` / `localhost:8082` → **ECONNREFUSED** |
| 回归 | 存量 **284 单测全绿** |

### 5.2 压测（c=10，20s，关闭跳转防刷，同一短码）

| 场景 | QPS | P50(ms) | P95(ms) | P99(ms) | 错误率 |
|---|---|---|---|---|---|
| ① 直连 core | **2910.4** | **3.17** | **5.35** | 8.78 | 0% |
| ② 经网关 | **2133.7** | **4.36** | **7.06** | 9.93 | 0% |
| ③ 同步统计（enabled=false） | 1236.2 | 7.30 | 12.86 | 15.93 | 0% |
| ④ MQ 异步 · Rabbit | 2133.7 | 4.36 | 7.06 | 9.93 | 0% |
| ⑤ MQ 异步 · Kafka | 1450.0 | 6.18 | 11.17 | 15.49 | 0% |

> ②④是同一组配置（异步+Rabbit+经网关），复用同一次结果。
> c=50 的补充数据：直连 2508 / 网关 2783 QPS——客户端已饱和，差异不可信，**不作为结论**。

### 5.3 结论

1. **多一跳的代价**：P50 +1.19ms、P95 +1.71ms，同并发下 QPS 约 -27%。绝对值不大，但这是"鉴权前置 + 统一入口"必须付的钱。
2. **异步化的收益**：同步 1236 QPS → 异步 2134 QPS（**+73%**），P50 7.30 → 4.36ms。印证了 V2"统计放跳转线程会掉 30%+"的判断。
3. **Rabbit vs Kafka（本机单节点）**：Rabbit 2134 QPS / P50 4.36ms，Kafka 1450 QPS / P50 6.18ms。小消息场景 Rabbit 更快；Kafka 的优势在吞吐和堆积能力，但本次 **单消费者消费不过来**——压测后 lag 达 **17,871** 条（生产 ~29k / 消费 ~11k），说明消费端并发需要调大才能发挥 Kafka 的堆积优势。

---

## 6. 学到了什么（面试要点）

1. **"只暴露网关"才是身份透传的完整答案**：网关剥离伪造头只是应用层，真正让"下游不能被直连"成立的是网络层（compose 不给 auth/core 映射端口）。
2. **容器化暴露了本地跑不出来的问题**：`optional:nacos` 的容错承诺，在本地因为 Nacos 一直有配置而没被验证；一进容器立刻暴露。
3. **压测要比"同一口径"**：并发 50 时客户端先饱和，直连/网关的差异是噪声；降到 10 才看到真实的 +1.2ms。**先判断瓶颈在不在被测系统**。
4. **Shell 也会制造"框架 bug"**：`set VAR=value && cmd` 的尾随空格让条件注解读到 `"false "`，现象看起来像"Spring 不认环境变量"。

---

## 7. 下一版预告

- V7 是路线图最后一站。后续可选：P4-1（secret 加密）、P4-2（签名含 body）、M3/P4-5（按压测数据决定是否加缓存）、Kafka 消费并发调优。
