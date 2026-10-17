# V5（P5）MQ 可切换（RabbitMQ ↔ Kafka）+ 死信/幂等

> 日期：2026-10-17 ｜ 版本：V5 ｜ 对应阶段：P5
> 一句话：把点击统计链路的 MQ 抽成薄接口，RabbitMQ 保留为基线、Kafka 作为第二实现，配置一键切换，并为 Kafka 补齐与 RabbitMQ 死信语义对齐的 retry topic + DLT。

---

## 1. 做了什么

1. **发布抽象**：新增 `VisitMessagePublisher` 接口，Rabbit/Kafka 各一套实现；`AsyncVisitRecorder` 改为依赖接口
2. **消费抽象**：把 `VisitStatsConsumer` 里的业务逻辑抽成 MQ 无关的 `VisitMessageHandler`，两套监听器退化为薄壳
3. **切换维度**：新增 `link.core.mq.type=rabbit|kafka`（默认 rabbit），用 `AllNestedConditions` 把 `enabled` 与 `type` 两个条件 AND 起来；`type` 取值 fail-fast
4. **Kafka 接入**：`KafkaVisitMessagePublisher` + `KafkaVisitStatsConsumer` + `KafkaMqConfig`（NewTopic / 错误处理 / 序列化）
5. **重试与死信**：手写 `KafkaRetryRecoverer`——失败发 retry topic（带 `x-retry-count` 头），超上限发 DLT
6. **基础设施**：`deploy/kafka/`（KRaft 单节点 compose + README）

---

## 2. 产出物

| 类型 | 内容 |
|---|---|
| 抽象层 | `mq/VisitMessagePublisher`、`mq/consumer/VisitMessageHandler`、`mq/message/VisitMessage.create()` 静态工厂 |
| Rabbit 侧 | `VisitMessageProducer`（实现接口）、`VisitStatsConsumer`（薄壳）、`RabbitMqConfig`（加 rabbit 条件） |
| Kafka 侧 | `KafkaVisitMessagePublisher`、`KafkaVisitStatsConsumer`、`KafkaRetryRecoverer`、`KafkaMqConfig` |
| 配置 | `LinkCoreProperties.Mq` 增 `type/topic/retryTopic/dltTopic/maxRetry/consumerGroup` + `validateMqType()`；`MqConditions` |
| 基础设施 | `deploy/kafka/docker-compose.yml`、`deploy/kafka/README.md` |
| 测试 | `VisitMessageHandlerTest`(6)、`VisitMessageTest`(2)、`KafkaVisitMessagePublisherTest`(2)、`KafkaRetryRecovererTest`(3)、`MqTypeWiringTest`(5) |

---

## 3. 决策与理由

| # | 决策 | 理由 |
|---|---|---|
| D1 | 自写薄接口，不用 Spring Cloud Stream | 与现有 `VisitRecorder`/`IdempotentChecker` 风格一致；Stream 要重写 Rabbit 代码、引入 Function 编程模型，binder 上配死信更绕 |
| D2 | 消费业务抽 `VisitMessageHandler` | 幂等、日期口径、`markDirty→recordVisitStrict` 顺序、失败 release 再抛——这些"值钱逻辑"只写一份 |
| D3 | Kafka 手写 `ConsumerRecordRecoverer`，不用 `@RetryableTopic` | `DeadLetterPublishingRecoverer` 默认只发 `<原topic>.DLT`，做不出分级；`@RetryableTopic` 是框架魔法，自动建 topic 不利于讲清 |
| D4 | `enabled` / `type` 两条正交维度分开 | `enabled` 决定"走 MQ 还是同步直写"，`type` 决定"哪种 MQ"；混在一起会把故事讲糊 |
| D5 | 复用 `IdempotentChecker` | 它是 Redis SETNX、与 MQ 无关；Kafka 至少一次语义下必然可能重复，幂等是必需 |
| D6 | `@KafkaListener` 用 SpEL 读 bean（`#{@linkCoreProperties.mq.topic}`） | 占位符 `${link.core.mq.topic}` 要求该 key 在 Environment 里；SpEL 直接读 `@ConfigurationProperties` bean，单一配置源、不依赖 Nacos 是否配了这个 key |

---

## 4. 踩坑（现象 · 原因 · 解决）

1. **Kafka 没有原生死信队列**：`DeadLetterPublishingRecoverer` 默认只发 `<原topic>.DLT`，无法表达"重试 3 次再进死信"。解决：手写 `ConsumerRecordRecoverer`，用 `x-retry-count` 头决定去 retry 还是 DLT。
2. **`@ConditionalOnProperty` 表达不了"两个条件 AND"**：`enabled` 和 `type` 要同时满足。解决：`AllNestedConditions` 包两层 `@ConditionalOnProperty`。
3. **`type=kafka` 时 Rabbit 侧必须不装配**：否则连不上 RabbitMQ 会启动失败。验证：**停掉 Kafka 跑 rabbit 模式**，core 仍正常启动并统计成功（`pv=1`）——反证 Kafka Bean 没被装配。
4. **KRaft 单节点的三个必填项**：固定 `CLUSTER_ID`、副本因子必须为 1（否则 `__consumer_offsets` 创建失败）、`KAFKA_ADVERTISED_LISTENERS` 必须含 `localhost:9092`（否则宿主机客户端连到容器内 IP）。
5. **Kafka 镜像来源**：Docker Hub 直连不通；`apache/kafka` **在** daocloud 白名单内（`bladex/sentinel-dashboard` 不在，所以 Sentinel 才改用 jar）。
6. **Nacos 回读校验的 `contentMatch` 对行尾敏感**：本地文件 CRLF 与 Nacos 存的 LF 不同会导致 `contentMatch=False`，但内容其实是合法的（用 curl 直接拉取核对过）。
7. **（顺带发现，P2 遗留）Feign 超时配置缩进错误**：`feign.openfeign.client.config.*` 不是合法的 Spring Cloud OpenFeign 路径（应为 `spring.cloud.openfeign.*` 或旧式 `feign.client.config.*`），当前实际用的是默认超时。已登记台账，未在本版改动。

---

## 5. 验证数据

| 项 | 结果 |
|---|---|
| 全量单测 | **267 全绿**（gateway `6/0/0/0`、auth `28/0/0/0`、core `233/0/0/1`） |
| Kafka 模式 E2E | topic 3 个自动创建（`link.visit` / `.retry` / `.dlt`）；跳转→消费→DB `pv=1`；consumer lag = **0** |
| Rabbit 模式 + Kafka 停机 | core **正常启动**，跳转→消费→DB `pv=1`（证明 `type=rabbit` 不装配 Kafka） |
| 装配矩阵单测 | rabbit（默认）/ kafka / enabled=false 三组合 + type 拼错 fail-fast，5 用例 |
| 红线断言迁移 | 6 条（顺序/异常 release+throw/重复双 never/空消息/日期口径）逐条迁到 `VisitMessageHandlerTest`，**未削弱** |

> 说明：E2E 里 3 次跳转只产生 1 条统计，是 P4 的**跳转防刷**（同 IP 同短码 1 秒内去重）在生效，不是丢消息。

---

## 6. 学到了什么（面试要点）

1. **"可切换"的关键是抽象接缝选对**：发布侧抽接口、消费侧抽业务处理器，两套 MQ 就只是"传输层插件"；如果直接在 `@RabbitListener` 里写业务，切换就得重写。
2. **Kafka 的"死信"要自己造**：没有 DLX，只能靠 retry topic + header 计数 + DLT；这正好可以和 RabbitMQ 的 `x-dead-letter-exchange` 并排讲清两种 MQ 的语义差异。
3. **条件装配要做"反向验证"**：光测"kafka 模式能跑"不够，还要测"rabbit 模式在 Kafka 挂掉时能启动"，才能证明没有误装配。
4. **配置中心回读校验要区分"内容错"和"格式差异"**：CRLF/LF 造成的 `contentMatch=False` 不是配置损坏，要能分辨，否则会误判。

---

## 7. 下一版预告（V6 / P6）

- **Vue3 管理后台 + 数据看板**：登录/租户管理/短链管理/渠道效果报表，打通 P4 埋下的渠道维度数据。
