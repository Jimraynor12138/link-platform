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
- ✅ V1（P1）：包名/配置前缀规范化（`com.aaf.link.*` / `link.core.*`）+ link-auth 认证链路与租户模型（双 Token / 刷新轮换 / 登出撤销 / 多租户越权防护；core 198 + auth 28 单测、E2E 9 步）
- ✅ V2（P2）：前置技术债清理 + Nacos 注册与配置中心 + OpenFeign 服务间调用（配置动态刷新、容错模式；core 198 + auth 28 单测、E2E 7 项 + 容错 2 场景）
- ✅ V3（P3）：Gateway 路由 + 鉴权前置（身份透传 + 防伪造）+ Sentinel 两层限流与熔断降级（gateway 6 + auth 28 + core 202 单测、E2E 10 项）
- ⏳ V4（P4）：core 多租户（tenant_id + 渠道）+ API Key

## 本地启动

前置：MySQL（库 `link_platform`）、Redis（db 1）、Nacos（Docker）。

```bash
# 1) 起 Nacos（注册中心 + 配置中心）
docker compose -f deploy/nacos/docker-compose.yml up -d

# 2) 发布配置（首次，或改动 deploy/nacos/config/ 之后）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\nacos\import-configs.ps1

# 3) 构建并启动服务
mvn clean install -DskipTests
java -Dfile.encoding=UTF-8 -jar link-auth/target/link-auth-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
java -Dfile.encoding=UTF-8 -jar link-core/target/link-core-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

> **`-Dfile.encoding=UTF-8` 不能省**：JDK 17 + 中文 Windows 的默认字符集是 GBK，
> 会导致 Nacos 上的中文配置解析失败（`MalformedInputException`），配置静默回退默认值。
>
> **容错模式**：Nacos 未启动时服务仍能启动（注册失败仅告警），但拿不到远端配置；
> 首次运行请先复制 `application-dev.example.yml` 为 `application-dev.yml` 并填入本地凭据（该文件已被 .gitignore 排除）。

## 文档

- `docs/01-需求与架构.md`：产品定位、能力对比、商业卖点、V0~V7 版本路线图
- `docs/02-开发日志.md`：各版本索引（含详细 devlog 链接）
- `docs/03-技术点与面试素材.md`：技术取舍、踩坑、面试话术
- `docs/plans/`：每个阶段的方案文档
- `docs/test/`：每个版本的测试报告（压测报告在 `docs/benchmark/`）
- `docs/devlog/`：每个版本的详细留痕（决策 / 踩坑 / 验证数据 / 面试要点）
