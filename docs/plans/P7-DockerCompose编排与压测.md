# P7（V7）方案：Docker Compose 编排 + 压测对比

> 本文是 P7 阶段的方案文档（入库权威版）。对应版本 V7；前置版本 V6。

---

## 1. 目标

把"本地多进程 + 手工起中间件"升级为**一条命令起全栈**，并用真实压测回答两个问题：
1. 微服务拆分（多一跳网关）的 RT 代价有多大？
2. 统计链路异步化 / MQ 选型，对跳转 QPS 的实际影响？

---

## 2. 范围（已确认）

| 项 | 选择 |
|---|---|
| 范围 | **A+B+C**：全服务 Compose + 压测 + README；网络收口；前端 Nginx 托管 |
| 中间件 | **全容器化**：MySQL/Redis/RabbitMQ/Nacos/Kafka 全进 compose |
| 压测工具 | **自写 Node 脚本**（零下载，统计 QPS/P50/P95/P99） |
| 对比口径 | ① 直连 core vs 经网关 ② 同步 vs MQ 异步 ③ Rabbit vs Kafka |

**不做**：P4-1（secret 加密）、P4-2（签名含 body）、M3（权限缓存）、P4-5（secret 缓存）——前者本轮未选，后两者要"先有压测数据再决定"。

---

## 3. 编排设计

### 3.1 服务与端口

| 服务 | 镜像 | 宿主机端口 | 说明 |
|---|---|---|---|
| link-mysql | mysql:8.0 | 3306 | 初始化挂 `deploy/sql`（01→04） |
| link-redis | redis:7-alpine | 6379 | 独立实例，db 1 |
| link-rabbit | rabbitmq:3.13-management-alpine | 5672/15672 | **非 guest 用户** |
| link-nacos | nacos/nacos-server:v2.4.2 | 8848/9848/9849 | 配置需单独发布 |
| link-kafka | apache/kafka:3.8.0 | 9092 | 容器内用 `link-kafka:19092` |
| link-auth | 自建 | **不映射** | 网络收口 |
| link-core | 自建 | **不映射** | 网络收口 |
| link-gateway | 自建 | **8080** | 唯一业务入口 |
| link-web | nginx:1.27-alpine | **80** | 静态托管 + 反代 |

> 只暴露 80/8080 给"业务访问"；中间件端口为便于本机排查保留映射，生产应关闭。

### 3.2 镜像构建

- 宿主机先 `mvn clean package -DskipTests`，Dockerfile **只 COPY jar**（不在容器内跑 Maven，避免重下依赖）
- layered jar：`java -Djarmode=layertools -jar app.jar extract`
- **关键**：解包后删除 `application/BOOT-INF/classes/application-dev.yml`，避免把本地凭据打包进镜像（同时不影响本地 `java -jar` 开发）
- Boot 3.2.5 的启动器是 `org.springframework.boot.loader.launch.JarLauncher`
- `ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"`（固化 P2-1）、`ENV TZ=Asia/Shanghai`
- 前端：`node:20-alpine` 构建 → `nginx:1.27-alpine` 托管

### 3.3 配置注入（全部用环境变量，不加配置文件）

- `SPRING_PROFILES_ACTIVE=container`：覆盖 `application.yml` 里写死的 `dev`，避免镜像内的 dev 配置生效
- `LINK_AUTH_JWT_SECRET`：**auth 与 gateway 必须同一个**（≥32 字节），否则网关全 401
- `NACOS_SERVER_ADDR=link-nacos:8848`、`SPRING_DATA_REDIS_HOST=link-redis`、`SPRING_DATASOURCE_URL=jdbc:mysql://link-mysql:3306/...`
- `SPRING_RABBITMQ_USERNAME/PASSWORD`（**不能用 guest**，跨容器被拒）
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=link-kafka:19092`（容器内 listener）
- `LINK_CORE_DOMAIN=localhost:8080`（短链走网关）
- `LINK_CORE_INTERNAL_EXPOSE_CONFIG=false`（关掉配置快照回显）
- 压测用：`LINK_CORE_MQ_ENABLED` / `LINK_CORE_MQ_TYPE` / `LINK_CORE_ANTI_ABUSE_ENABLED`

> Spring Boot 宽松绑定下 **OS 环境变量优先级高于 Nacos 引入的配置**，所以上述变量能稳定压过 `deploy/nacos/config/*.yaml`。

### 3.4 Nginx

```nginx
location /        { try_files $uri $uri/ /index.html; }
location /auth/   { proxy_pass http://link-gateway:8080; }
location /api/    { proxy_pass http://link-gateway:8080; }
location /open/   { proxy_pass http://link-gateway:8080; }
```
短链域名固定 `localhost:8080`，**不把 `/{code}` 代理到 Nginx**（否则与 SPA 的 `/link` 冲突）。

---

## 4. 压测设计

`bench/loadtest.mjs`：`--url --concurrency --duration --warmup`，undici keep-alive，输出 QPS/P50/P95/P99/错误率 + JSON。

| 口径 | 做法 |
|---|---|
| 直连 vs 网关 | `docker-compose.bench.yml` 临时给 core 加 `8082:8082`，压完移除再验证收口 |
| 同步 vs 异步 | `LINK_CORE_MQ_ENABLED=false/true` 各一轮 |
| Rabbit vs Kafka | `LINK_CORE_MQ_TYPE=rabbit/kafka` 各一轮（Kafka 轮记录 lag） |

**压测前置**：固定短码并预热缓存；**关闭跳转防刷**（否则同 IP 同码 1 秒只记一次，会把"统计开销"抹掉）；每轮之间清统计 key。

---

## 5. 验收标准

| 项 | 标准 |
|---|---|
| 一键启动 | 全部服务 healthy |
| 页面 | `http://localhost` 可登录/建链/看板 |
| 跳转 | `http://localhost:8080/{code}` 302 正常 |
| 收口 | `localhost:8081` / `localhost:8082` **连接被拒** |
| 压测 | 三组对比数据齐全且为真实跑出 |
| 回归 | 存量 284 单测全绿 |
| 文档 | P7 方案/devlog/测试报告/技术点/台账/README |

---

## 6. 坑清单

1. `LINK_AUTH_JWT_SECRET` 无默认值，auth/gateway 必须同值
2. `spring.profiles.active: dev` 写死 → 容器用 `SPRING_PROFILES_ACTIVE=container` 覆盖
3. `application-dev.yml` 会被打进 jar → Dockerfile 解包后删除
4. RabbitMQ `guest` 仅允许回环 → 必须建非 guest 用户
5. Kafka 双 listener → 容器内必须 `link-kafka:19092`
6. Nacos 2.x 需 9848/9849
7. MySQL init 脚本按字母序，保持 01~04 前缀
8. `link.core.domain` 改值会让 Redis 缓存 key 变化（命中率骤降，属预期）
9. 防刷会污染压测 → 关闭
10. 上 compose 前必须停本地 8080/8081/8082/3306/6379/5672 与 Docker 里的 8848/9092
11. `docker compose down` 不删 volume，重置数据用 `down -v`
12. 新增镜像需先探测 daocloud 白名单，拉不到就停下来确认
13. **压测数值不可虚构**，跑不出来如实写
