# 社交圈（H5 社交网站）

移动端 H5 社交平台，对标微信朋友圈 + 私聊 + 语音/视频通话。前端 H5（Vue 3 + Vite + TypeScript + Vant），后端 Spring Boot 3 多模块。部署目标：阿里云 ECS（Ubuntu）+ 已备案域名。

> 当前进度：**阶段一~七已完成**。生产部署配置就绪（`deploy/`），待按 [DEPLOY.md](deploy/DEPLOY.md) 在阿里云执行。

## 目录结构

```
├── backend/                          # Spring Boot 多模块后端
│   ├── social-common/                #   通用：Result/错误码/异常/JWT
│   ├── social-service/               #   业务：实体/Mapper/AuthService/UserService
│   ├── social-api/                   #   接口：Controller/DTO/拦截器/全局异常/CORS/Swagger
│   ├── social-websocket/             #   实时通信：WebSocket/STOMP 鉴权骨架
│   └── social-application/           #   启动模块：可执行 Jar + 配置
├── frontend/                         # Vue 3 + Vite + TS 前端
└── docs/sql/schema.sql               # MySQL 全量建表脚本
```

## 本地启动

### 1. 数据库

```bash
# 需要本地 MySQL 8 + Redis
mysql -uroot -p < docs/sql/schema.sql
```

### 2. 后端（端口 18081）

```bash
cd backend
./mvnw -pl social-application -am spring-boot:run -Dspring-boot.run.profiles=dev
# 或打包运行：
./mvnw clean package
java -jar social-application/target/social-application-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

接口文档（dev 环境）：http://localhost:18081/swagger-ui.html

本地 MySQL/Redis 默认连接 `localhost:3306/root/root` 与 `localhost:6379`，见 `backend/social-application/src/main/resources/application-dev.yml`。

### 3. 前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 。`/api` 与 `/ws` 已配置代理到后端 18081。

### 4. 联调验证码

dev 环境 `app.verify-code.mock-enabled=true`，验证码固定为 `123456`（注册/登录通用）。

### 5. 认证模式切换（手机 / 邮箱）

改后端配置 `app.auth.mode`（生产为 `.env` 的 `AUTH_MODE`）即可切换：

- `phone`（默认）：手机号注册/登录 + 短信验证码（保留原有逻辑）
- `email`：邮箱注册/登录 + 邮箱验证码（SMTP 真实发信，见下）

前端登录/注册页启动时通过 `GET /api/auth/config` 自动读取当前模式，无需改前端代码。
发起会话按**昵称或邮箱**搜索（手机号精确匹配保留，老账号仍可搜到）。
邮箱/手机号均加密存储 + SHA-256 检索键；切换前需在远程库执行 `docs/sql/migration_phase8_email.sql`（新增 `email_hash`、`phone` 允许为空）。

### 6. 邮箱验证码真实发送（SMTP）

`email` 模式下验证码走 SMTP 真实发信，配置分两处：

- **SMTP 服务器**：`spring.mail.host/port/username/password`（QQ 邮箱默认 `smtp.qq.com:465`；密码填邮箱「设置→账号→开启 SMTP 服务」生成的**授权码**，非登录密码）
- **业务开关/发件人**：`app.mail.enabled`（true=真实发信）、`app.mail.from`（发件人，通常与 SMTP 账号一致）

`app.mail.enabled=true` 且发件人已配置 → 生成随机验证码并真实发信，接口返回「验证码已发送」；
否则退回 mock 固定码 `123456`（本地联调，开发模式适用）。

dev 环境已在 `application-dev.yml` 填好 QQ 授权码（可用 `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` 环境变量覆盖）；
生产由 `.env` 的 `MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM / MAIL_ENABLED` 注入（见 [deploy/.env.example](deploy/.env.example)），授权码不落明文配置。
163 / 126 等其它服务商改 `MAIL_HOST` / `MAIL_PORT` 即可。

### 7. 个人信息设置与昵称管理

- **个人信息设置**：「我的 → 编辑资料」可更换头像（上传到 `avatar` bucket）、修改昵称与个性签名。
- **昵称修改间隔**：`app.nickname.change-interval-days`（生产为 `.env` 的 `NICKNAME_CHANGE_INTERVAL_DAYS`，默认 `7` 天，`0`=不限制）。用户每次自行改名后记录 `user_profile.nickname_updated_at`，间隔内再次修改会被拒绝；**管理员不受此限制**。
- **管理员改昵称**：管理后台「用户管理 → 改昵称」可修改任意用户昵称（不受间隔限制），操作写入操作日志。
- **违禁昵称定时任务**：每 30 分钟巡检昵称，命中敏感词（`sensitive-words.txt`）的自动改为「违禁昵称+毫秒时间戳」，并记录操作日志；改名不计入用户的修改间隔，用户可立即再自行改名。
- 需在远程库执行 `docs/sql/migration_phase9_nickname.sql`（新增 `nickname_updated_at`，幂等）。

## 阶段一已实现功能

**后端**
- 多模块 Maven 骨架（common / service / api / websocket / application）
- 统一返回 `{code, message, data}`、统一错误码、全局异常处理、参数校验
- JWT 双令牌（access 2h + refresh 7d 轮换）、认证拦截器、登出失效
- 注册 / 密码登录 / 验证码登录 / 刷新令牌 / 获取与更新资料 / 隐私设置
- 验证码 mock 模式（预留阿里云短信接入点）
- WebSocket/STOMP 骨架：`/ws` 端点 + JWT 握手鉴权 + 用户定向回显测试
- Swagger 文档、CORS 配置、MyBatis-Plus 自动填充/逻辑删除

**前端**
- Vue3 + Vite + TS + Vant 4 + Pinia + Vue Router
- 登录 / 注册页（含 60s 倒计时获取验证码）
- 底部 TabBar 布局（首页 / 消息 / 发布 / 我的）
- Axios 统一封装：自动带 token、401 静默刷新并重放、刷新失败跳登录
- 路由守卫、明暗双主题（主色 #1e90ff）

**数据库**：全量 12 张核心表 + 索引说明（`docs/sql/schema.sql`），覆盖账号、关系、隐私、朋友圈、聊天、通话。

## 阶段二已实现功能

**后端**
- 朋友圈发布：图文 / 纯文字 / 转发三种类型，公开 / 仅好友 / 私密三种可见范围
- 信息流：关注流（好友+自己）与推荐流（公开+好友可见），游标分页（时间+ID 防重复）
- 点赞：幂等切换（重复点赞自动取消），计数原子自增不依赖读值
- 评论：顶级评论 + 楼中楼（parentId + replyTo），作者/回复人昵称回填
- 转发：生成 type=2 转发动态，原动态转发计数自增，详情可溯源 origin 作者
- 删除：软删除（status=3）本人动态，评论仅本人可见逻辑删除
- 举报接口（content 审核位预留）
- 文件上传：本地存储抽象（`StorageService` 预留 OSS 实现），按 `uploads/{bucket}/yyyy/MM/dd/uuid.ext` 落盘，静态资源直链访问，multipart 20MB 限制

**前端**
- 首页信息流：关注 / 推荐双 Tab，下拉刷新 + 上滑加载更多（`van-pull-refresh` + `van-list`），回到首页自动刷新
- 朋友圈卡片：作者头像昵称、可见范围图标、九宫格图片（单图定宽展示）、转发来源卡片
- 点赞动效：心形放大回弹 + 六向粒子迸发，已赞标红可取消
- 评论弹层：下拉加载评论列表，点击回复楼中楼（"回复 xxx" 前缀），输入自动增高
- 转发弹层：可填转发语 + 选择可见范围
- 发布页：多图上传（≤9 张，图片类型与 20MB 校验、即时上传），可见范围单选，发布成功回首页
- 图片预览：`van-image-preview` 全屏查看

> 联调说明：本地无 MySQL/Redis/Docker，后端冒烟测试走远程库（`8.142.176.238`）。远程库已建测试账号 `13900001605 / test1234`、`13900001606 / test1234`，可直接登录互聊联调。

## 阶段三已实现功能

**后端**
- 会话：双人会话唯一化（user_a < user_b 唯一键），获取/创建/详情，列表按最后消息时间倒序
- 消息：文本/图片（语音视频通话留待阶段四），`clientMsgId` 幂等防重（重复请求返回已存在消息）
- 历史消息：游标分页（id 倒序 + 升序时间线），会话内按时间拉取
- 已读回执：`markRead` 批量置已读，推送 READ 事件给对端
- 实时推送：消息入库（事务提交后）发布事件 → `SimpMessagingTemplate` 定向推送到 `/user/{id}/queue/messages`，双方在线即达
- 离线补偿：消息落库 + 未读计数，重连后会话列表/历史接口补齐
- 在线状态：STOMP 连接/断开 → Redis presence 键（5min TTL），会话列表返回 `peerOnline`
- 用户搜索：昵称/手机号模糊匹配（排除自己与封禁账号），发起新会话入口
- WebSocket：`/ws` 原生端点（token 走查询参数）+ `/ws-sockjs` SockJS 兜底，JWT 握手鉴权

**前端**
- 消息 Tab：会话列表（头像/昵称/最后消息摘要/相对时间/未读数/在线绿点），下拉刷新，未读数驱动 TabBar 红点
- 发起会话：右上角 + 弹层搜索用户（昵称/手机号），点击直接进入聊天
- 聊天窗口：历史消息分页加载（上滑拉更早）、实时收发、消息类型区分（文本气泡/图片缩略图+全屏预览）
- 发送：Enter 发送文本，图片按钮选图（类型与 20MB 校验）上传后发图，客户端幂等 ID 防重
- 已读状态：自己最后一条消息显示 发送中/未读/已读，收到 READ 回执后全部置已读
- STOMP 连接管理：单例自动重连（5s 退避），重连读取最新 token（配合 http 401 静默刷新）
- 登出清理：断开 STOMP、清空会话与未读

> 技术说明：`/ws` 已从 SockJS 改为原生 WebSocket（H5 + Nginx WSS 更简洁）；`/ws-sockjs` 保留供受限网络降级。

## 阶段四已实现功能

**后端**
- 通话状态机（CallService）：`callId` 维度维护进行中通话（内存 ConcurrentHashMap），忙碌判断（主/被叫任一在通话即拒绝），在线判断复用 Redis presence
- 信令（STOMP `/app/call` → `/user/{id}/queue/call`）：invite / accept / reject / cancel / offer / answer / candidate / end 全生命周期分发透传，失败回执 busy / offline 推给主叫
- 身份：`@MessageMapping` 走消息线程，用 `Principal`（不可用 HTTP 的 UserContext）
- 通话记录：`call_record` 落库（呼叫中/接通/拒绝/未接/挂断/掉线 6 态），挂断/掉线记录时长
- 断线掉话：WS 断开 → PresenceListener 调用 `dropUserCall`，通话标记掉线（status 5）并推送 end 给对端
- ICE 配置：`app.webrtc.ice-servers`（application.yml 可配）+ `GET /api/call/config` 下发

**前端**
- CallStore：状态机（idle/outgoing/incoming/active）+ RTCPeerConnection（本地/远端流、ICE 候选队列——远端描述未就绪前先缓存）+ WebRTC 协商时序控制
- 信令接入：stomp 单例新增 `/user/queue/call` 订阅与 `publishSignal`，Chat/Call 双回调不互覆
- CallPanel：全屏面板（呼出/来电/通话中），语音拨号页 + 视频远端全屏/本地小窗，接听/拒绝/取消/挂断，通话计时，合成铃声
- 入口：聊天窗口在线时头部提供语音/视频按钮，App.vue 挂载 CallPanel 于路由之上（任何页面可接听来电）

**已知限制（后续阶段补齐）**
- 本地/同网 N 2 N 可达；跨运营商 NAT 需 TURN（阶段六自建 coturn 后替换 STUN-only 配置）
- 通话未写入聊天会话（消息类型 5），未提供通话历史查询
- 忙线/超时依赖对端在线与 STOMP 长连接，超时振铃逻辑待完善

## 阶段五已实现功能

**前端（纯 CSS 动效，零新增依赖）**
- 全局 page 滑动淡入过渡（250-300ms ease-out）+ 卡片柔和阴影
- 朋友圈：卡片入场 fade-in-up、图片九宫格弹性展开、评论弹层自动聚焦输入框
- 聊天：新消息气泡弹入动画
- 会话列表：`van-swipe-cell` 滑动露出删除按钮（带弹簧回弹），删除确认后本地移除
- 通话面板：来电呼吸灯光圈 + 全屏抖动、视频接通本地小窗弹入、按钮按压反馈
- 性能：注册 van-lazyload（图片懒加载）、桌面端容器居中适配（max-width 520px）
- 暗色主题补漏：硬编码 `#999` 统一为 `var(--text-secondary)`

**后端（会话滑动删除支撑）**
- conversation 表新增 `deleted_a` / `deleted_b` DATETIME 软删字段；`DELETE /api/chat/{conversationId}`
- 软删后对方发新消息（`last_message_time > deleted_at`）会话自动重新可见
- 迁移脚本：`docs/sql/migration_phase5_conversation_delete.sql`（需在远程库执行一次）

## 阶段六：生产部署（deploy/）

整套阿里云 Ubuntu 22.04 部署配置见 [deploy/DEPLOY.md](deploy/DEPLOY.md)：

- **docker-compose.yml**：mysql / redis / app（多阶段构建）/ coturn / nginx 五服务，仅 80/443/3478 对外
- **nginx/default.conf**：全站 HTTPS、gzip、静态缓存、`/api`、`/uploads` 反代、`/ws`+`/ws-sockjs` WebSocket 升级
- **coturn/turnserver.conf**：TURN/STUN 中继，静态凭据（动态 time-limited 凭据留后续）
- **deploy.sh**：一键生成 `.env` → 渲染配置 → `docker compose up -d --build`
- **ssl/issue-cert.sh**：certbot 签发或对接阿里云免费 DV 证书

```bash
cd deploy
cp .env.example .env   # 逐项填写
./deploy.sh
```

## 阶段七：合规与运维

围绕《网络安全法》等要求的合规能力，已随部署配置就绪：

- **内容审核（先发后审）**：本地 DFA 敏感词库（`sensitive-words.txt`）命中 → 动态置「审核中」仅作者可见；管理后台通过/拒绝；定时巡检兜底
- **实名认证**：姓名+身份证 AES-GCM 加密登记（当前 mock 通过，接入阿里云实人认证后置 `REALNAME_MOCK=false`）
- **举报/封禁**：动态卡片举报入口；管理后台封禁用户立即生效（Redis `ban:user:{id}` 中断在线会话）
- **数据合规**：手机号加密 + `phone_hash` 查重；注销账号抹除敏感信息；操作日志留存 180 天自动清理
- **管理后台**：H5 内嵌 `/admin`，含 用户管理 / 内容审核 / 举报处理 / 操作日志
- **监控运维**：`deploy/monitor.sh` + MySQL 慢查询日志 + Redis maxmemory，详见 [deploy/ops.md](deploy/ops.md)
- **迁移 SQL**：`docs/sql/migration_phase7_compliance.sql`（幂等），老用户手机号由启动器自动加密回填

## 后续阶段

| 阶段 | 内容 | 状态 |
|---|---|---|
| 一 | 项目骨架 + 数据库 + 登录注册 + 基础框架 | ✅ |
| 二 | 朋友圈：发帖、信息流、点赞、评论、转发 | ✅ |
| 三 | 即时聊天：会话、消息、实时推送、离线补偿 | ✅ |
| 四 | 音视频通话：WebRTC 信令 + TURN 降级 | ✅（TURN 阶段六已配 coturn） |
| 五 | UI 打磨 + 动效 + 暗色主题 + 性能优化 | ✅ |
| 六 | 阿里云生产部署：docker-compose + Nginx + coturn + SSL | ✅（配置就绪，待在服务器执行） |
| 七 | 合规与运维：内容审核、实名、举报封禁、监控 | ✅ |

详细技术方案见根目录 `prompt-社交网站-DeepSeek.md`。
