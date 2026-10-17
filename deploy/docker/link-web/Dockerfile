# ============================================================
# 管理后台镜像：只托管静态资源（dist 在宿主机构建）
#
# 为什么不在容器里跑 Node：
#   1) 省一个 node 镜像（约 200MB）与一次 npm ci
#   2) 与后端一致：产物在宿主机构建，容器只负责运行
#   → 构建前请先执行：cd link-web && npm run build
# ============================================================
FROM nginx:stable-alpine

COPY deploy/docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY link-web/dist /usr/share/nginx/html

EXPOSE 80
