# P5（V5）方案：MQ 可切换（RabbitMQ ↔ Kafka）+ 死信/幂等

> 本文是 P5 阶段的方案文档（入库权威版）。对应版本 V5；前置版本 V4（P4）。

---

## 1. 目标

兑现产品卖点「**中间件可切换，为不同规模客户提供同构不同配的部署**」：

1. 统计链路的 MQ 抽象成薄接口，**RabbitMQ 保留为基线、Kafka 作为第二实现**，配置切换。
2. 为 Kafka 补齐与 RabbitMQ 死信语义对齐的「**重试主题 + 死信主题（retry topic + DLT）**」。
3. 复用与 MQ 无关的 Redis 幂等（`IdempotentChecker`）。

---

## 2. 四个已确认的选择

| 问题 | 选择 |
|---|---|
| link-stats 是否抽取 | **不抽**，消费仍在 core（最小闭环，P6 看板时再评估） |
| MQ 抽象方式 | **自写薄接口**（不用 Spring Cloud Stream） |
| Kafka 运行环境 | **Docker 单节点 KRaft**（daocloud 源可用，`apache/kafka:3.8.0`） |
| 死信深度 | **重试主题 + 死信主题**（对齐 RabbitMQ DLX 语义） |

**不做**：link-stats 抽取、前端（P6）、全服务编排与压测（P7）、RocketMQ（已决策不引入）、Spring Cloud Stream、Sentinel 规则持久化。

---

## 3. 两条正交维度（本版最容易讲糊的地方）

| 维度 | 配置 | 含义 | 现状 |
|---|---|---|---|
| 维度一 | `link.core.mq.enabled` | 走 MQ 还是**同步直写**（`AsyncVisitRecorder` / `SyncVisitRecorder`） | P2 已有 |
| 维度二 | `link.core.mq.type` | **哪种 MQ**（rabbit / kafka） | **P5 新增** |

> 混为一谈会出现"以为在切 MQ，其实切成了同步"的错误理解。

---

## 4. 抽象层设计

```
RedirectServiceImpl
  └─ VisitRecorder（已有接缝）
       ├─ AsyncVisitRecorder（mq.enabled=true）
       │    └─ VisitMessagePublisher（P5 新增接口）
       │         ├─ VisitMessageProducer        [rabbit 实现]
       │         └─ KafkaVisitMessagePublisher  [kafka 实现]
       └─ SyncVisitRecorder（mq.enabled=false，同步直写 Redis）

消费侧（薄壳 + 共用业务逻辑）：
  VisitStatsConsumer       [@RabbitListener，rabbit]
  KafkaVisitStatsConsumer  [@KafkaListener，kafka]
       └─ VisitMessageHandler（P5 新增，MQ 无关：幂等→解析日期→markDirty→recordVisitStrict→异常 release 再抛）
```

**关键点**：消费业务逻辑（幂等、跨零点日期、`markDirty`/`recordVisitStrict` 顺序、失败 release 再抛）**只写一份**，两套监听器退化为"取消息 → 交 handler"的薄壳。

---

## 5. Kafka 的 retry topic + DLT

Kafka **没有原生死信队列**（与 RabbitMQ 最大语义差异），需自己搭：

```
link.visit ──消费失败──▶ link.visit.retry（header 带重试次数）
                              └──超过 maxRetry──▶ link.visit.dlt
```

- 实现：`DefaultErrorHandler` + 自定义 `ConsumerRecordRecoverer`（**必须自定义路由**——默认只发 `<原topic>.DLT`，做不出"retry → 超限 → dlt"的分级）
- 与 RabbitMQ 并排对照：

| | RabbitMQ（基线） | Kafka（P5） |
|---|---|---|
| 重试 | Spring AMQP 重试 + `default-requeue-rejected=false` | `DefaultErrorHandler` 重试 + 自定义 recoverer |
| 死信 | 队列参数 `x-dead-letter-exchange` → `link.visit.dlx` | 手动发到 `link.visit.retry` → `link.visit.dlt` |
| 幂等 | `IdempotentChecker`（Redis SETNX） | **同一个** `IdempotentChecker` |
| 重试前释放幂等 | 是（否则重试被自己挡掉） | 是（同语义） |

---

## 6. 装配矩阵（必须实测）

| `mq.enabled` | `mq.type` | 装配结果 |
|---|---|---|
| `false` | — | 仅 `SyncVisitRecorder`；两套 MQ 实现都不装配 |
| `true`（默认） | `rabbit`（`matchIfMissing`） | Rabbit 拓扑 + 生产者 + 监听器 |
| `true` | `kafka` | Kafka 生产者 + 监听器 + 错误处理；**Rabbit 拓扑与监听器不装配**（否则连不上 Rabbit 会启动失败） |

用 `AllNestedConditions` 组合 `enabled` 与 `type` 两个 `@ConditionalOnProperty`（单注解表达不了 AND）。

**`mq.type` 取值 fail-fast**：非 `rabbit|kafka` 时启动即报错（沿用 `JwtProperties` 的既有模式），避免拼错导致"零实现"而报错晦涩。

---

## 7. 配置归属（遵循 docs/03 §8.7）

| 配置 | 归属 |
|---|---|
| `link.core.mq.*`（type/topic/retryTopic/dltTopic/maxRetry/consumerGroup） | **Nacos** `link-core.yaml`（业务配置） |
| `spring.kafka.bootstrap-servers`（连接信息） | 本地 `application-dev.yml`（与 MySQL/Redis/RabbitMQ 一致） |
| `spring.kafka.*` 序列化 / trusted packages / auto-create | 本地 `application.yml`（框架级） |

**不能热切换**：`@ConditionalOnProperty` 是启动期装配，改 `mq.type` 必须**重启**。

---

## 8. 序列化

- RabbitMQ：`Jackson2JsonMessageConverter`（JSON，管理台可读）
- Kafka：`JsonSerializer` / `JsonDeserializer`；`VisitMessage` 含 `LocalDateTime` → **必须注册 `JavaTimeModule`**，并配置 `spring.json.trusted.packages`
- 两种 MQ **共用同一个 `VisitMessage` DTO**（切换只换传输层，消息契约不变）

---

## 9. 验收标准

| 项 | 标准 |
|---|---|
| 存量回归 | core `220/0/0/1`、auth `28/0/0/0`、gateway `6/0/0/0`（合计 **254 不因 P5 变红**） |
| 装配 | 三种组合实测（rabbit / kafka / enabled=false），kafka 模式下 Rabbit 拓扑不装配 |
| Kafka 消费 | 切 kafka 后跳转 → 消费 → Redis + `t_link_stats` 正确 |
| 幂等 | 重复投递同一消息 → PV 不重复累加 |
| 重试与死信 | 制造失败 → 进 retry topic 并按次数重试 → 超限进 DLT（非无限重投） |
| 回退 | 切回 `type=rabbit` 重启 → 基线工作 |
| 文档 | `docs/devlog/V5-P5-*.md`、`docs/test/V5-测试报告.md`、`docs/03` P5 技术点 + 台账、索引/路线图/README |

---

## 10. 坑清单

1. **Kafka 无原生死信队列**：必须自己搭 retry + DLT；`DeadLetterPublishingRecoverer` 默认只发 `<原topic>.DLT`，要自定义 destination 才能分级
2. **`@ConditionalOnProperty` 启动期装配**：改 `mq.type` 不能热切换，必须重启
3. **`type=kafka` 时 Rabbit 拓扑必须不装配**，否则连不上 Rabbit 导致启动失败
4. **Kafka 镜像来源**：Docker Hub 直连不通；`apache/kafka` 在 daocloud 白名单内（Sentinel 的 bladex 镜像不在，故 Sentinel 才用 jar）
5. **KRaft 单节点**：固定 `CLUSTER_ID`、副本因子必须为 1，否则 broker 起不来 / 内部 topic 创建失败
6. **序列化 JavaTime**：`LocalDateTime` 必须注册 `JavaTimeModule`
7. **重试前必须 release 幂等标记**，否则第二次被自己挡掉
8. **Kafka 至少一次语义必然可能重复**，幂等是必需；`idempotentTtlSeconds` 要 ≥ 实际重投窗口
9. **consumer group.id 必须配**
10. **不要改坏存量**：Rabbit 拓扑名与消息契约不变，`type` 默认 rabbit
11. **工程规则**：`-pl` 配 `-am`、`-Dtest` 加 `failIfNoSpecifiedTests=false`、`-Dfile.encoding=UTF-8`、`.ps1` 字面量 ASCII
12. **抽 `VisitMessageHandler` 会改 `VisitStatsConsumerTest`**：断言要完整迁移，不能削弱
