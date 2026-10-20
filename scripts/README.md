# 演示数据脚本

两个脚本，分别解决"**历史**"和"**实时**"：

| 脚本 | 作用 | 数据是否真实 |
|---|---|---|
| `seed-demo.mjs` | 通过真实接口创建 3 个租户 + 12 条短链，并生成历史统计回填 SQL | 租户/短链是**真的**（走发号器、多租户归属）；历史统计是**回填的模拟值** |
| `simulate-clicks.mjs` | 轮询所有短码发起真实跳转 | **真实**：走完整链路（网关 → core → 防刷 → MQ → 消费 → Redis → 每 2s flush → MySQL） |

## 用法

```bash
# 1) 造租户与短链（幂等，重复执行只会跳过已存在的）
node scripts/seed-demo.mjs

# 2) 回填过去 14 天统计（不含今天）
docker exec -i link-mysql mysql -uroot -p<密码> < deploy/sql/seed/01-stats-backfill.sql

# 3) 造今天的真实点击（轮数 × 12 个短码）
node scripts/simulate-clicks.mjs 5

# 4) 等 2~3 秒（快照 flush 周期），刷新看板
```

## 两个必须知道的坑

### 1. 防刷：同 IP + 同码 1 秒内只记一次

`link.core.anti-abuse.window-seconds = 1`。所以：

- 同一个短码连点 10 次 → **PV 只 +1**
- `simulate-clicks.mjs` 的做法是"一轮点所有码 + 轮间等 1.2 秒"，让每个码都能记上

想临时关掉防刷（压测用）：

```bash
# 用命令行参数覆盖（环境变量在条件装配上不可靠，见 devlog 坑 3）
docker compose -f deploy/docker/docker-compose.yml run --rm link-core --link.core.anti-abuse.enabled=false
```

### 2. 快照是"只增不减"的

`LinkStatsMapper.upsertStats` 里：

```sql
pv = IF(new.pv >= t_link_stats.pv, new.pv, t_link_stats.pv)
```

含义：如果 Redis 里的实时 PV **小于** 库里已有的值，就不更新（防止 Redis 被清空导致统计倒退）。

**所以不要回填"今天"**：一旦今天被写成 300，真实点击只有个位数，就永远显示不出变化。
`seed-demo.mjs` 已经按"只回填到昨天"生成 SQL。

## 数据链路（点击之后发生了什么）

```
GET /{code}
  → 网关路由
  → core: 布隆判空 → 本地缓存 → Redis → DB
  → 防刷判断（shouldRecord）
  → 发 MQ（Rabbit 或 Kafka，link.core.mq.type）
  → 消费：幂等 → markDirty → 写 Redis 统计（PV/UV/IP/来源/设备）
  → 每 2s 快照 flush → t_link_stats
  → 看板按 date / channel 聚合
```

## 重置

```bash
# 只清统计，保留租户与短链
docker exec -i link-mysql mysql -uroot -p<密码> -e "USE link_platform; TRUNCATE t_link_stats;"

# 全清（含租户/短链），重新 seed
docker exec -i link-mysql mysql -uroot -p<密码> -e "USE link_platform; DELETE FROM t_link_stats WHERE 1=1; DELETE FROM t_link;"
node scripts/seed-demo.mjs
```
