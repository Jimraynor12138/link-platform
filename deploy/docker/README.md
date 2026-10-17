# 一键启动（Docker Compose）

## 前置

- Docker Desktop（Compose v2）
- JDK 17 + Maven（构建后端 jar）
- Node 18+（构建前端 dist）
- 所需镜像：`mysql:8.4`、`redis:8`、`nginx:stable-alpine`、`eclipse-temurin:17-jre`、`nacos/nacos-server:v2.4.2`、`apache/kafka:3.8.0`、`rabbitmq:3.13-management-alpine`
  （前六个本机通常已有；**只有 rabbitmq 一般需要下载**。Docker Hub 直连不通时，用 `docker.m.daocloud.io` 拉取后 `docker tag`）

## 启动

```bash
# 1) 构建后端 jar + 前端 dist（都在宿主机执行，容器只拷贝产物）
cd <repo>
mvn clean package -DskipTests
cd link-web && npm install && npm run build && cd ..

# 2) 准备变量（全部有默认值，可跳过）
cd deploy/docker
cp .env.example .env        # Windows: copy .env.example .env

# 3) 起全栈
docker compose up -d --build

# 4) 首次启动后发布 Nacos 配置（否则业务配置走代码默认值）
#    Windows:
powershell -NoProfile -ExecutionPolicy Bypass -File ../nacos/import-configs.ps1
#    Linux / Git Bash:
bash ../nacos/import-configs.sh

# 5) 重启 core 让远端配置生效（optional:nacos 是启动期加载）
docker compose restart link-core link-auth
```

## 访问

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost |
| 短链跳转 | http://localhost:8080/{code} |
| 健康检查 | http://localhost:8080/actuator/health |
| Nacos 控制台 | http://localhost:8848/nacos（nacos/nacos） |

演示账号：`superadmin / 123456`（平台）、`tenantadmin / 123456`（租户）。

## 端口与网络收口

- **对外**：`80`（link-web）、`8080`（link-gateway）
- **`link-auth` / `link-core` 不映射宿主机端口** → `curl localhost:8081` / `localhost:8082` 会连接被拒（这是 P3-1/P2-2 的修复，不是故障）
- 中间件（MySQL/Redis/RabbitMQ/Kafka）只在 `link-net` 内可达，不映射宿主机；Nacos 因要发布配置保留了 8848/9848/9849

调试中间件：

```bash
docker exec -it link-mysql mysql -uroot -plink123456 link_platform
docker exec -it link-redis redis-cli -n 1 keys 'link:core:*'
docker exec -it link-rabbit rabbitmqctl list_queues
docker exec -it link-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## 常用命令

```bash
docker compose ps
docker compose logs -f link-core
docker compose down            # 保留数据卷
docker compose down -v         # 连数据一起清（重建数据库）
```

## 排障

| 现象 | 原因 |
|---|---|
| 登录成功但所有接口 401 | `LINK_AUTH_JWT_SECRET` 在 auth 与 gateway 不一致（或少于 32 字节，启动期就会报错） |
| core 启动报 Rabbit `ACCESS_REFUSED` | 用了 `guest` 账号；guest 只允许回环，必须用 `.env` 里的 `link` 账号 |
| 切 `mq.type=kafka` 后连不上 | 容器内必须用 `link-kafka:19092`（advertised 的 PLAINTEXT listener），不是 `localhost:9092` |
| 服务注册不上、配置读不到 | Nacos 2.x 需要 9848（gRPC），只放 8848 会出现"控制台能开、客户端连不上" |
| 短链域名是 8082 | `LINK_CORE_DOMAIN` 没设或没生效；容器内必须是 `localhost:8080` |
| 数据库表不存在 | `deploy/sql` 没挂上，或执行了 `down -v` 后没重建；首次启动看 `docker logs link-mysql` |

## 压测模式

```bash
# 额外暴露 core 8082（仅用于"直连 vs 网关"对比）
docker compose -f docker-compose.yml -f docker-compose.bench.yml up -d --force-recreate link-core

# 同步统计 / Kafka 等对比：用环境变量覆盖后重建 core
LINK_CORE_MQ_ENABLED=false docker compose up -d --force-recreate link-core
LINK_CORE_MQ_TYPE=kafka    docker compose up -d --force-recreate link-core
```

压测脚本见仓库根 `bench/loadtest.mjs`，说明见 `bench/README.md`。
