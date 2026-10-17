# 压测脚本

零依赖（只用 Node 18+ 内置 `fetch`），用于 P7 的三组对比。

## 用法

```bash
node bench/loadtest.mjs --url http://localhost:8080/abc123 \
  --concurrency 50 --duration 30 --warmup 5 \
  --label "经网关" --out bench/results/gateway.json
```

输出一行 Markdown（可直接贴进报告）+ 一行 JSON（便于脚本汇总）。

| 参数 | 默认 | 说明 |
|---|---|---|
| `--url` | 必填 | 压测目标（短链跳转用 `/{code}`） |
| `--concurrency` | 50 | 并发 worker 数 |
| `--duration` | 30 | 正式计时时长（秒） |
| `--warmup` | 5 | 预热时长（秒），预热数据丢弃 |
| `--label` | url | 报告里的标签 |
| `--out` | 空 | 结果 JSON 落盘路径 |

## 三组对比跑法

前置：先造一个短码并预热；**关闭跳转防刷**（`LINK_CORE_ANTI_ABUSE_ENABLED=false`），否则同 IP 同码 1 秒只记一次统计，会把被测变量抹掉。

```bash
# ① 直连 core vs 经网关（需要临时暴露 8082）
docker compose -f deploy/docker/docker-compose.yml -f deploy/docker/docker-compose.bench.yml up -d --force-recreate link-core
node bench/loadtest.mjs --url http://localhost:8082/<code> --label "直连 core"
node bench/loadtest.mjs --url http://localhost:8080/<code> --label "经网关"
# 压完恢复收口
docker compose -f deploy/docker/docker-compose.yml up -d --force-recreate link-core

# ② 同步统计 vs MQ 异步
LINK_CORE_MQ_ENABLED=false docker compose -f deploy/docker/docker-compose.yml up -d --force-recreate link-core
node bench/loadtest.mjs --url http://localhost:8080/<code> --label "同步统计"
LINK_CORE_MQ_ENABLED=true docker compose -f deploy/docker/docker-compose.yml up -d --force-recreate link-core
node bench/loadtest.mjs --url http://localhost:8080/<code> --label "MQ 异步"

# ③ Rabbit vs Kafka
LINK_CORE_MQ_TYPE=rabbit docker compose -f deploy/docker/docker-compose.yml up -d --force-recreate link-core
node bench/loadtest.mjs --url http://localhost:8080/<code> --label "Rabbit"
LINK_CORE_MQ_TYPE=kafka docker compose -f deploy/docker/docker-compose.yml up -d --force-recreate link-core
node bench/loadtest.mjs --url http://localhost:8080/<code> --label "Kafka"
docker exec -it link-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group link-core-stats
```

## 已知限制（写进结论，不回避）

- 客户端是单进程 Node，**QPS 上限受脚本自身限制**，绝对值不代表服务上限，只用于**同条件下横向对比**
- 统计只做客户端观测，未接入 JVM 指标；若需要更细的 GC/线程数据，应接 Prometheus + JMeter
