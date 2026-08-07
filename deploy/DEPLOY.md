# 阶段六：生产部署指南（阿里云 Ubuntu 22.04）

本文档配套 `docker-compose.yml` / `deploy.sh` / `nginx/default.conf` / `coturn/turnserver.conf` / `ssl/issue-cert.sh`,从零部署到生产。

## 1. 前置：阿里云控制台操作位置

### 1.1 创建 ECS
- **入口**:阿里云控制台 →「云服务器 ECS」→ 创建实例
- 推荐:2 核 4G 起步(Ubuntu 22.04 64 位),绑定弹性公网 IP
- **安全组放行端口**(控制台 → ECS 实例 → 安全组 → 配置规则 → 入方向):

| 端口 | 协议 | 用途 | 建议 |
|---|---|---|---|
| 443 | TCP | HTTPS / WSS | 全网 |
| 80 | TCP | HTTP(301 跳转 + ACME) | 全网 |
| 3478 | UDP + TCP | TURN 中继 | 全网 |
| 22 | TCP | SSH | **仅限自己 IP** |

> 安全组是白名单,云服务商层拦截先于系统防火墙。MySQL/Redis 等容器**不**暴露公网端口,只走内网。

### 1.2 域名解析
- **入口**:阿里云控制台 →「域名」→ 解析设置
- 添加 `A` 记录:`@` / `www` → ECS 公网 IP

### 1.3 备案
- 服务器在中国大陆,**必须 ICP 备案**(域名已在备案中)。
- 网站页脚展示备案号并链接到 beian.miit.gov.cn(阶段七落地,阶段六建议先放占位)。

## 2. 环境变量(.env)

```bash
cd deploy
cp .env.example .env
vim .env     # 逐项填写
```

| 变量 | 必填 | 说明 |
|---|---|---|
| `ECS_PUBLIC_IP` | ✅ | ECS 公网 IP,coturn `external-ip` |
| `DOMAIN` | ✅ | 已解析域名,Nginx server_name / coturn realm / 证书名 |
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root 密码(强随机) |
| `MYSQL_USER` / `MYSQL_PASSWORD` | ✅ | 应用数据库账号 |
| `REDIS_PASSWORD` | ✅ | Redis 密码 |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | ✅ | JWT 双密钥(≥32 字符,两把不同) |
| `CORS_ALLOWED_ORIGINS` | ✅ | `https://你的域名.com` |
| `VERIFY_CODE_MOCK` | 默认 true | 阶段六先用 mock 验证码联调;阶段七接短信后置 false |
| `SMS_SIGN_NAME` / `SMS_TEMPLATE_CODE` | 阶段七 | 阿里云短信 |
| `TURN_URL` / `TURN_USERNAME` / `TURN_PASSWORD` | 建议 | 填了则跨 NAT 可中继;留空前端退化为公共 STUN |

## 3. SSL 证书

**推荐:阿里云免费 DV 证书**(国内可达,不依赖 certbot 出站)
1. 控制台 →「数字证书管理服务」→ 创建证书 → 免费证书(DV 单域名)
2. 域名校验 → 签发 → 下载 **Nginx** 格式
3. 上传到 `deploy/ssl/certs/`:
   - `fullchain.pem` → 重命名为 `${DOMAIN}.crt`
   - `privkey.pem` → 重命名为 `${DOMAIN}.key`

或用 certbot 自动签发:`cd deploy && ./ssl/issue-cert.sh`。

## 4. 一键部署

```bash
cd deploy
./deploy.sh
```

脚本会:校验 docker → 生成 `.env`(若缺)→ 用 python3 渲染 nginx/coturn 配置到 `generated/` → 检查证书 → `docker compose up -d --build`。

## 5. 服务清单

| 容器 | 端口(对外) | 说明 |
|---|---|---|
| nginx | 80 / 443 | 前端静态 + 反代 + WSS |
| app | —(内网 18081) | Spring Boot 后端(prod profile) |
| mysql | —(内网 3306) | MySQL 8,首次自动建表 |
| redis | —(内网 6379) | Redis 7,AOF 持久化 |
| coturn | 3478(host 网络) | TURN/STUN 中继 |

## 6. 上线检查清单

- [ ] `https://域名` 打开,H5 正常
- [ ] 注册/登录(验证码 mock=123456)/发朋友圈/发消息/图片上传 冒烟通过
- [ ] **浏览器控制台无混合内容报错**;WebSocket 显示 `wss://`(而非 ws://)
- [ ] TURN 自测:`turnutils_uclient -u <TURN_USERNAME> -p <TURN_PASSWORD> <ECS_PUBLIC_IP> -y`
- [ ] 数据库每日备份(见下)
- [ ] `docker compose restart` 后服务自动拉起(`restart: always`)
- [ ] 日志轮转已配(json-file 50m×5)
- [ ] 慢查询:MySQL 开 `slow_query_log`;Redis `maxmemory` 已设
- [ ] 监控:`docker stats` 脚本 或 ECS 云监控

### 数据库备份(建议加 cron)
```bash
# 每天 03:30 全量备份,保留 7 天
30 3 * * * docker compose -f /opt/social/deploy/docker-compose.yml exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" social | gzip > /backup/social-$(date +%F).sql.gz && find /backup -name 'social-*.sql.gz' -mtime +7 -delete
```

## 7. 常见问题

- **H5 连不上 WebSocket**:检查 Nginx `location /ws` 是否带 `Upgrade` / `Connection "upgrade"` 头;证书是否有效(浏览器绿锁);是否走了 `wss://`
- **上传图片 404**:确认 `location /uploads/` 反代到 `app:18081`,且 app 的 `/app/uploads` 卷有写权限
- **TURN 不通**:优先排查安全组 3478 是否放行、`external-ip` 是否填了 ECS 公网 IP、`user=...` 凭据与前端是否一致
- **首次启动 mysql 未建表**:数据卷非空时 `/docker-entrypoint-initdb.d/` 不会执行——确认 `mysql-data` 卷是全新创建的
