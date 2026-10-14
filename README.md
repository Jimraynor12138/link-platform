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

开发中 — P0：多模块骨架与短链核心迁移

## 文档

- `docs/01-需求与架构.md`
- `docs/02-开发日志.md`
