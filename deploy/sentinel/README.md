# Sentinel Dashboard

Sentinel Dashboard **没有官方 Docker 镜像**，且本地 Docker Hub 镜像源不可达（daocloud 白名单也不含它），
故采用**官方 jar 直跑**——这本来就是 Sentinel 官方文档的启动方式，比 Docker 更可靠。

## 获取

下载与客户端版本一致的 dashboard jar（本项目 Sentinel 客户端为 **1.8.8**）：

```
https://github.com/alibaba/Sentinel/releases/download/1.8.8/sentinel-dashboard-1.8.8.jar
```

放到本目录，文件名 `sentinel-dashboard-1.8.8.jar`（已被 `.gitignore` 排除，不入库）。

## 启动

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\sentinel\run-dashboard.ps1
# 或直接：
java -Dserver.port=8858 -jar sentinel-dashboard-1.8.8.jar
```

- 控制台：http://localhost:8858（账号/密码 `sentinel` / `sentinel`）
- **端口用 8858**：Sentinel Dashboard 默认 8080，与网关冲突。

## 注意事项（面试要讲）

1. **规则默认存内存，Dashboard 重启即丢**。本项目因此把限流规则同时写在代码里
   （`@PostConstruct` 加载 `GatewayFlowRule` / `FlowRule`），**可复现、不依赖 Dashboard 也能跑**；
   Dashboard 的价值是"可视化 + 动态调参"。
2. **生产应把规则持久化到 Nacos**（`sentinel-datasource-nacos`），本项目未接（P3 边界，但可讲做法）。
3. 各服务客户端通过 `spring.cloud.sentinel.transport.dashboard=localhost:8858` 上报；
   同机多服务的 transport 端口（默认 8719）会顺延，Dashboard 上会看到多个实例。
