# 方案文档索引

> 每个阶段的方案存放于此（入库版），便于阅读、复盘与面试前快速回忆。
> 执行结果与踩坑见 `docs/devlog/`；测试数据见 `docs/test/`；技术取舍见 `docs/03-技术点与面试素材.md`。

| 阶段 | 方案文档 | 状态 | 对应开发日志 |
|---|---|---|---|
| P0 / V0 | [P0-多模块骨架与核心迁移](./P0-多模块骨架与核心迁移.md) | ✅ 已完成 | [V0-P0](../devlog/V0-P0-多模块骨架与核心迁移.md) |
| P1 / V1 | [P1-link-auth认证与租户模型](./P1-link-auth认证与租户模型.md) | ✅ 已完成 | [V1-P1](../devlog/V1-P1-link-auth认证与租户模型.md) |
| P2 / V2 | [P2-Nacos与Feign通路](./P2-Nacos与Feign通路.md) | ✅ 已完成（含前置技术债清理） | [V2-P2](../devlog/V2-P2-Nacos注册配置与Feign通路.md) |
| P3 / V3 | [P3-Gateway与Sentinel](./P3-Gateway与Sentinel.md) | 🚧 进行中 | [V3-P3](../devlog/V3-P3-*.md) |
| P4 / V4 | core 多租户 + 渠道 + API Key 配额与防刷 | ⏳ 待开始 | — |
| P5 / V5 | 统计链路 MQ 可切换（RabbitMQ ↔ Kafka，不引入 RocketMQ） | ⏳ 待开始 | — |
| P6 / V6 | Vue3 管理后台 + 看板 | ⏳ 待开始 | — |
| P7 / V7 | Docker Compose 编排 + 压测对比 | ⏳ 待开始 | — |
