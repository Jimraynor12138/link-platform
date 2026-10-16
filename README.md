# link-platform 多租户短链平台

多租户 SaaS 短链平台：**租户 → 活动 → 渠道 → 短链 → 点击统计**。
微服务架构，含管理后台前端与开放 API（API Key）。

## 模块

| 模块           | 说明                                         |
| -------------- | -------------------------------------------- |
| link-common    | 公共依赖：统一返回、异常、租户上下文、工具类 |
| link-gateway   | 网关：路由、JWT 校验、限流                   |
| link-auth      | 租户 / 用户 / 角色 / 权限、双 Token、API Key |
| link-core      | 短链核心：发号、缓存、跳转、布隆过滤器、降级 |
| link-stats     | 统计消费与数据看板查询                       |
| link-admin-web | 管理后台（Vue 3 + Element Plus + ECharts）   |
| deploy         | docker-compose、Nginx、SQL 初始化脚本        |

## 技术栈

Java 17 · Spring Boot 3 · Spring Cloud Alibaba（Nacos / Sentinel）· Spring Cloud Gateway · OpenFeign ·
MyBatis-Plus · MySQL 8 · Redis · RabbitMQ（可切换 Kafka）· Redisson · Docker Compose · Vue 3

## 状态

- ✅ V0（P0）：多模块骨架 + 短链核心迁移 + 公共类上移（198 单测）
- ✅ V1（P1）：包名/配置前缀规范化（`com.aaf.link.*` / `link.core.*`）+ link-auth 认证链路与租户模型（双 Token / 刷新轮换 / 登出撤销 / 多租户越权防护；core 198 + auth 23 单测、E2E 9 步）
- ⏳ V2（P2）：Nacos 注册与配置中心 + OpenFeign 服务间调用

## 文档

- `docs/01-需求与架构.md`：产品定位、能力对比、商业卖点、V0~V7 版本路线图
- `docs/02-开发日志.md`：各版本索引（含详细 devlog 链接）
- `docs/03-技术点与面试素材.md`：技术取舍、踩坑、面试话术
- `docs/plans/`：每个阶段的方案文档
- `docs/test/`：每个版本的测试报告（压测报告在 `docs/benchmark/`）
- `docs/devlog/`：每个版本的详细留痕（决策 / 踩坑 / 验证数据 / 面试要点）
