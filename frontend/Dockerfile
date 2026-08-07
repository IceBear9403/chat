# =============================================================
# 阶段六：前端静态文件镜像（多阶段构建）
#
# 构建上下文 = frontend/
#   1. Node 构建 dist
#   2. nginx 托管静态文件（Nginx 配置由 compose 挂载 deploy/nginx/default.conf）
# =============================================================

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.25-alpine
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
