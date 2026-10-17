# Kafka（P5：MQ 可切换的第二实现）

> 定位：**基础设施容器化**（与 `deploy/nacos/`、`deploy/sentinel/` 一致）。
> RabbitMQ 仍是本地安装的**基线实现**，Kafka 仅作为 `link.core.mq.type=kafka` 时的第二实现。

---

## 1. 获取镜像

Docker Hub 直连不通，本项目实测 **daocloud 源可用**（`apache/kafka` 在其白名单内）：

```bash
docker pull docker.m.daocloud.io/apache/kafka:3.8.0
docker tag  docker.m.daocloud.io/apache/kafka:3.8.0 apache/kafka:3.8.0
```

> 备注：`bladex/sentinel-dashboard` 不在 daocloud 白名单（Sentinel 因此改用 jar 直跑），
> 但 `apache/kafka` **在**，所以 Kafka 可以走容器。

## 2. 启动 / 停止

```bash
docker compose -f deploy/kafka/docker-compose.yml up -d
docker compose -f deploy/kafka/docker-compose.yml down
```

## 3. 验证

```bash
# 容器状态（等 healthy）
docker ps --filter name=link-kafka --format "{{.Names}} | {{.Status}}"

# 列出 topic（此时应为空，topic 由应用启动时 NewTopic 创建）
docker exec link-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## 4. 设计说明（为什么这么配）

| 配置 | 原因 |
|---|---|
| **KRaft + `CLUSTER_ID` 固定值** | 单节点免 ZooKeeper，少一个容器；固定 ID 让"重建环境"结果可复现 |
| **三个监听器**（`CONTROLLER` / `PLAINTEXT` / `PLAINTEXT_HOST`） | `CONTROLLER` 给 KRaft 仲裁；`PLAINTEXT` 给容器内互访；**`PLAINTEXT_HOST` 是宿主机入口** |
| **`KAFKA_ADVERTISED_LISTENERS` 含 `localhost:9092`** | 客户端连上后，broker 会返回"你应该连我哪个地址"；不写宿主机可解析的地址，客户端会连到容器内部 IP 而失败 |
| **`KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`** | 单节点若用默认副本因子 3，内部 topic 创建会失败，broker 起不来 |
| **`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`** | topic 由应用显式声明（`NewTopic`）；拼错名字会立刻报错，而不是静默建一个错 topic |
| **`KAFKA_NUM_PARTITIONS=3`** | 单机演示够用，且能观察"同 group 多消费者分摊分区"的语义 |

## 5. 已知限制

- **单节点 = 副本因子 1**：broker 挂掉数据不可用，仅用于本地验证，不代表生产高可用。
- **规则/数据不持久化到外部**：`kafka-data` 是 Docker volume，`down -v` 会清空。
- **不做 ACL/SASL**：本地 `PLAINTEXT`，生产应启用安全协议。
- **不做全服务编排**：这里只起 Kafka 基础设施；业务服务的 Docker 编排留给 P7。
