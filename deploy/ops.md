# 运维与合规指南（阶段七）

> 配套脚本：`deploy/monitor.sh`。本文覆盖日常巡检、故障排查、合规运营与数据安全。
> 生产主机：阿里云 Ubuntu 22.04，Docker Compose 编排，`deploy/docker-compose.yml`。

---

## 1. 日常监控

### 一键巡检
```bash
cd deploy
./monitor.sh          # 单次巡检：容器健康/重启次数/宿主内存磁盘/慢查询/Redis 内存
./monitor.sh status   # 只打印状态，不告警
./monitor.sh watch    # 每 60s 持续巡检
```

### 接入 cron 定时巡检
```bash
crontab -e
# 每分钟一次，异常输出到日志；可配置 ALERT_WEBHOOK 推送到钉钉/企微
* * * * * cd /root/social/deploy && ./monitor.sh >> /var/log/social-monitor.log 2>&1
```

### 可选告警
在 `.env` 增加 `ALERT_WEBHOOK=你的机器人webhook`，monitor.sh 会在发现异常时 POST 告警文本。

---

## 2. 常用排障命令

```bash
# 全部服务状态
docker compose -f deploy/docker-compose.yml ps

# 后端日志（最近 200 行，跟随）
docker logs --tail 200 -f social-app

# MySQL 慢查询（>1s，已开启 slow-query-log）
docker exec social-mysql tail -f /var/log/mysql/slow.log

# 直接查库（迁移 SQL / 排查数据）
docker exec -it social-mysql mysql -usocial -p social

# Redis 内存与键
docker exec -it social-redis redis-cli -a "$REDIS_PASSWORD" INFO memory

# 容器资源占用
docker stats --no-stream
```

---

## 3. 合规运营（阶段七新增）

### 内容审核（先发后审）
- 发布动态时本地 DFA 敏感词库命中 → `moment.status=0`（待审核），仅作者可见，展示「审核中」徽标。
- 管理员在 **管理后台 → 内容审核** 通过 / 不通过。
- 词库：`backend/social-service/src/main/resources/sensitive-words.txt`（按行维护，重启生效）。
- 兜底巡检：定时任务每 30 分钟重扫已发布动态，词库更新后历史命中项自动回退待审核。

### 举报处理
- 用户端动态卡片「…」→ 举报（可选填理由）。
- 管理员在 **管理后台 → 举报处理** 删除动态（逻辑删除）或忽略。

### 用户封禁
- 管理员在 **管理后台 → 用户管理** 封禁/解封。
- 封禁写入 Redis `ban:user:{id}`，已登录会话的后续请求立即被拦截（无需等 token 过期）。
- 注销账号：用户「我的 → 注销账号」输入密码确认；置 `status=2` 并抹除姓名/身份证。

### 实名认证
- 当前为 mock 通过（记录姓名+身份证，AES-GCM 加密）。
- 接入阿里云实人认证：置 `REALNAME_MOCK=false` 并在 `UserService.bindRealName` 中接入三要素核验。

### 操作日志
- `user_log` 表记录 注册/登录/登出/实名/注销/封禁/审核/举报 等操作（含 IP、UA）。
- 留存 180 天，每天 03:05 定时清理（`ComplianceScheduler.purgeExpiredLogs`）。

### 定时任务开关
多实例扩展时仅主实例运行合规定时任务，其余实例 `.env` 置 `COMPLIANCE_JOB_ENABLED=false`。

---

## 4. 数据备份与恢复

```bash
# 每日凌晨备份（crontab 示例）
30 2 * * * docker exec social-mysql mysqldump -usocial -p"$MYSQL_PASSWORD" social | gzip > /backup/social_$(date +\%F).sql.gz
# 保留最近 7 天
find /backup -name "social_*.sql.gz" -mtime +7 -delete
```

恢复：
```bash
gunzip -c /backup/social_2026-08-08.sql.gz | docker exec -i social-mysql mysql -usocial -p"$MYSQL_PASSWORD" social
```

---

## 5. PII 加密密钥管理（重要）

- 手机号/姓名/身份证用 `APP_PII_KEY`（≥16 字符）经 SHA-256 派生做 AES-256-GCM 加密。
- **密钥变更 = 历史密文永久不可解密**（无法重放解密）。上线前务必把密钥存入云 KMS / 密码管理器，严禁明文写在仓库、`.env` 之外的地方。
- 老用户明文手机号由启动器 `LegacyPhoneBackfillRunner` 自动回填（加密 + `phone_hash`），迁移见 `docs/sql/migration_phase7_compliance.sql`。

---

## 6. 上线检查清单（阶段七增补）

- [ ] 执行 `docs/sql/migration_phase7_compliance.sql`（幂等，可重复执行）
- [ ] `.env` 已配置 `APP_PII_KEY`（≥16 字符强随机）
- [ ] `frontend/.env.production` 已填 `VITE_ICP` 备案号并重新 `npm run build`
- [ ] 重新 `docker compose up -d --build`，`social-app` 健康检查通过
- [ ] 验证老用户仍能登录（backfill 回填正常）
- [ ] 用管理员账号（`is_admin=1`）登录，走通：用户封禁/解封 → 审核队列 → 举报处理 → 操作日志
- [ ] 发布含敏感词的动态 → 显示「审核中」，他人不可见；审核通过/拒绝生效
- [ ] 实名认证流程走通；注销账号后旧 token 失效
- [ ] monitor.sh 输出全绿，cron 已挂上
