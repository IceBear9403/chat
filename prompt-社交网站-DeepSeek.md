# DeepSeek v4 完整开发提示词：H5 社交网站（Spring Boot 后端 + 阿里云部署）

> 使用方式：将本文件完整内容一次性粘贴给 DeepSeek v4，按"交付要求"中的阶段分批执行。当前为 v1.0，含 WebRTC 通话与阿里云生产部署专项方案。

---

# 角色

你是一名资深全栈工程师，擅长移动端 H5 与高并发服务端开发。你精通 HTML5/CSS3/JavaScript（Vue3 或 React）、Spring Boot、WebSocket、WebRTC 等主流技术栈，对主流社交软件（微信、微博、小红书）的交互设计、动效规范和数据模型有深入理解。你将负责从零设计并实现一个完整的社交网站，代码要规范、可扩展、生产可用。

# 项目背景

我正在开发一个面向移动端的社交网站（H5 形态，浏览器访问，后续可套壳为 App），部署在阿里云中国大陆服务器上，供朋友共同使用。核心定位是"熟人 + 开放内容"相结合的社交平台，功能对标微信朋友圈 + 私聊 + 语音/视频通话。

# 技术栈（固定，不得擅自更改）

- 前端：H5（推荐 Vue 3 + Vite + TypeScript，或你更擅长的方案，需说明理由），UI 组件库可选 Vant / Element Plus，动画使用 CSS3 + GSAP 或 Vue Transition 组合，保证 60fps 流畅度
- 后端：Spring Boot 3.x + Java 17+
- 数据库：MySQL 8.x（主库）+ Redis 7.x（缓存 / 会话 / 实时在线状态 / 消息队列简化方案）
- 实时通信：WebSocket（聊天）+ STOMP 协议，断线重连与消息补偿
- 音视频通话：WebRTC（点对点），通过后端信令服务器交换 SDP/ICE，支持 STUN 打洞与 TURN 中继降级
- 文件存储：阿里云 OSS（接口抽象，预留本地磁盘切换能力），图片压缩/缩略图
- 认证：JWT + Refresh Token 双令牌机制
- 项目结构：Maven 多模块（如 social-common / social-api / social-service / social-websocket）

# 核心功能需求

## 1. 账号与个人中心

- 注册/登录（手机号或邮箱 + 验证码，密码加盐哈希；国内部署需手机号实名，见"合规硬性要求"）
- 个人资料：昵称、头像（支持上传裁剪）、个性签名、性别、地区
- 关注/粉丝体系，好友申请与同意流程
- 隐私设置：谁可以看我的朋友圈、谁可以私聊我

## 2. 朋友圈（信息流）

- 发布图文动态（最多 9 图，九宫格布局）、纯文字、转发动态
- 点赞、评论（支持楼中楼回复）、转发分享（站内转发 + 生成分享卡片带链接）
- 信息流拉取：关注流 + 推荐流（简单基于点赞/时间热度排序）
- 动态可见范围：公开 / 仅好友 / 私密
- 删除自己的动态，举报不良内容

## 3. 即时聊天

- 单聊（点对点），支持文字、图片、语音消息
- 已读/未读状态，消息时间线分组
- 会话列表（最近会话 + 未读红点 + 草稿）
- 消息可靠性：发送失败重试、离线消息拉取、消息幂等

## 4. 语音 / 视频通话（详见"WebRTC 专项方案"）

- 一对一音视频通话，WebRTC 点对点直连，支持降级到 TURN 中继
- 呼叫、接听、拒绝、挂断、超时未接逻辑
- 通话中的界面：静音、免提、摄像头切换（前后/开关）
- 通话时显示网络状态（音视频质量提示）

# UI/UX 要求（重点）

- 整体风格对标主流社交软件：底部 Tab 导航（首页/消息/发布/我的 等）、顶部状态栏、卡片式信息流、圆角与柔和阴影
- 主色调建议选用清新蓝色系（如 #1E90FF），明暗两套主题，暗色模式适配
- 动画要求：
  - 页面切换用平滑的滑动/淡入过渡（如 250-300ms ease-out）
  - 点赞要有"跳动+散花"微动效，评论输入框自动上弹
  - 发布动态时图片上传有进度动画，九宫格展开有弹性动画
  - 消息气泡弹出、会话列表滑动删除（带弹簧回弹）
  - 通话来电页有全屏抖动/呼吸灯效果
- 保证在低端安卓机也能流畅滚动（虚拟滚动、图片懒加载、长列表分页）
- 移动端优先，响应式适配桌面浏览器

# 数据模型（至少包含这些核心表）

- `user`、`user_profile`、`user_relation`（好友/关注）、`moment`（动态）、`moment_media`（动态图片/视频）、`moment_like`、`moment_comment`、`moment_repost`、`conversation`（会话）、`message`（消息）、`call_record`（通话记录）、`feed_rule`（可见范围/隐私设置）

# 后端接口约定

- RESTful 风格，统一返回格式：`{ code, message, data }`
- 统一异常处理、全局参数校验（Bean Validation）、分页参数规范
- 关键接口举例：登录、发朋友圈、点赞/取消点赞、评论/回复、获取信息流（游标分页）、获取会话列表、发送消息、获取历史消息、创建通话信令、上传文件、获取 ICE servers 配置
- 提供 OpenAPI（Swagger）接口文档

# 实时通信方案

- WebSocket 鉴权：连接时带 token，服务端校验
- 在线状态维护在 Redis（心跳超时标记离线）
- 消息推送：在线走 WebSocket 实时推，离线存库、上线拉取
- 信令服务器只做转发，不承载媒体流（P2P 直连）

---

# WebRTC 专项方案（音视频通话完整实现）

## A. 通话架构：P2P 优先 + 多级降级

采用"端到端直连优先，逐级降级"策略，保证任何网络环境都能通话：

```
P2P 直连（打洞成功，最优）
   ↓ 失败/超时
TURN 中继（服务器转发媒体流，保底可用）
   ↓ 视频不通/多端场景
SFU 选择性转发（预留演进，多人群聊/多人会议时启用）
```

- 通话发起顺序：先尝试 host → srflx（STUN 反射）→ relay（TURN），ICE 候选按优先级收集
- 若 ICE 协商超时（>8s）或双方无重叠候选，自动重协商切换到 TURN 中继模式
- 架构上预留 SFU 接口（如 mediasoup / Janus / LiveKit 的抽象层），现阶段单聊不启用，但接口设计为可替换

## B. 信令服务器（Spring Boot 端）

- 职责：仅交换 SDP 和 ICE 候选，**不承载媒体流**
- 基于现有 WebSocket 通道扩展消息类型（`call/invite`、`call/accept`、`call/reject`、`call/ice-candidate`、`call/sdp-offer`、`call/sdp-answer`、`call/hangup`）
- 信令流程：
  1. 主叫发送 `call/invite`（携带 callId、被叫 userId、媒体类型 audio/video、ICE servers 配置）
  2. 被叫收到来电推送，选择接听后回 `call/accept`，双方各自创建 RTCPeerConnection
  3. 主叫 createOffer → 发送 `call/sdp-offer`；被叫 setRemoteDescription + createAnswer → 回 `call/sdp-answer`
  4. 双方 ICE 候选通过 `call/ice-candidate` 双向交换（Trickle ICE）
  5. 任一方挂断发 `call/hangup`，服务端记录 `call_record` 并通知对端
- 超时逻辑：主叫 30s 无应答自动取消；被叫侧来电提示响铃 30s
- 通话状态机：`idle → calling → connecting → connected → ended`，服务端维护通话状态并做并发保护（同一用户同时只允许一个活跃通话）

## C. STUN / TURN 服务

- 内网/小规模部署：自建 coturn（开源），TURN 端口建议 3478（UDP/TCP）+ 443（TLS，穿透防火墙）
- 公网生产：直接购买云厂商 TURN 服务（七牛云、声网、腾讯云 TRTC），或自建 coturn 高可用
- TURN 凭据动态生成：后端为每次呼叫生成短期（如 24h）的 time-limited 凭据（REST API + HMAC 算法），前端 `pc.setConfiguration` 时注入，避免长期共享静态凭据
- ICE servers 配置下发：登录后或呼叫时由后端接口返回 `{ urls, username, credential }`

## D. 前端 WebRTC 实现（H5）

- 使用原生 WebRTC API（`RTCPeerConnection` / `getUserMedia`），不引入重型 SDK
- 媒体约束：
  - 语音：`audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }`
  - 视频：`video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }`，摄像头切换用 `video.setting` 重新采集
- 通话界面：
  - 小窗口悬浮视频（PiP 效果）、本地预览镜像、远端视频全屏
  - 静音 / 摄像头开关 / 扬声器切换 / 挂断按钮
  - 通话计时器、网络质量指示灯（由 webrtc-stats 换算）
- 离屏降载：切后台或锁屏时暂停摄像头采集、仅保留音频，节省功耗
- iOS/Android 兼容：处理 Safari 的 `playsinline` 属性、音频上下文在用户手势后才能激活的限制

## E. 质量监控与自适应

- 周期性（每 2s）调用 `pc.getStats()` 采集：RTT、抖动、丢包率、码率、分辨率
- 按丢包率/码率动态调节视频分辨率（如 720p → 480p → 320p）和帧率，丢包严重时临时只留音频
- 实时上报统计到后端（/call/stats），便于排查
- 通话结束生成摘要：总时长、网络评分（优/良/差）、断连次数

## F. 断线重连与异常恢复

- 心跳超时（如 5s 无 ICE 消息）判定链路异常，尝试重建 PeerConnection
- WebSocket 断线自动重连，重连成功后恢复信令通道并同步通话状态
- 通话过程中任一方 App 被杀：服务端 60s 内未收到心跳则向对端推送"对方已掉线"，通话标记为中断

## G. 兼容性与降级策略

- 检测不支持 WebRTC 的浏览器：能力检测后提示"当前浏览器不支持音视频通话，请使用 Chrome / Safari / 微信内置浏览器"
- H5 处于后台时被呼叫：利用 `navigator.serviceWorker` 推送通知（可选）+ 页面重新激活后展示未接来电记录
- 未接来电写入 `call_record`，在会话列表显示"未接来电（视频）"并支持一键回拨

## H. 部署建议

- coturn 需暴露公网端口，放通 UDP 3478 / TCP 3478 / TLS 443，并配置防火墙与安全组
- 生产环境 WebSocket 与信令走 HTTPS/WSS（443），避免混合内容被浏览器拦截
- 多人视频（未来）：替换信令为 Room 模型，媒体接入 SFU（mediasoup 或云厂商），前端从 P2P 模式平滑迁移

---

# 工程与质量要求

- 代码分层清晰（Controller / Service / Mapper / DTO / Entity）
- 敏感操作做幂等与防重（如重复点赞、重复发送消息）
- SQL 加必要索引，写出关键查询的建表语句与索引设计说明
- 提供统一的异常码字典表

---

# 阿里云生产部署方案

## A. 部署架构总览

```
用户浏览器(H5)
   │  HTTPS :443
   ▼
阿里云 SLB / Nginx(443, 含 WSS 升级)
   ├──► Spring Boot 应用容器 (API + WebSocket 信令)
   │        ├──► MySQL 8 (RDS 或自建容器)
   │        └──► Redis 7
   ├──► H5 静态文件 (打包产物, Nginx 托管)
   └──► coturn (TURN: UDP 3478 / TCP 3478 / TLS 443 / 27017)
```

- 单机部署即可支撑 100 人以内朋友使用：2 核 4G ECS 起步，建议 4 核 8G
- 系统：Ubuntu 22.04 LTS，全部服务用 Docker + docker-compose 编排

## B. 阿里云资源清单与安全组（务必写明）

| 资源 | 说明 |
|---|---|
| ECS | Ubuntu 22.04，绑定弹性公网 IP |
| 域名 | 已备案域名，解析到 ECS 公网 IP |
| 安全组放行端口 | `443/tcp`（HTTPS）、`80/tcp`（HTTP，仅用于 301 跳转）、`3478/udp`+`3478/tcp`（TURN）、`27017/udp+27017/tcp`（TURN 备用，可选）、`22/tcp`（SSH，建议仅限自己 IP） |
| 所有 Docker 服务端口 | **禁止暴露到公网**，仅 127.0.0.1 或 Docker 内网互通，统一由 Nginx 入口 |

## C. Docker Compose 编排（核心）

`docker-compose.yml` 定义 5 个服务，写清镜像版本、数据卷、健康检查、restart 策略：

1. `mysql`：MySQL 8，数据卷持久化，初始化建表脚本自动执行，时区 `Asia/Shanghai`
2. `redis`：Redis 7，开启持久化（AOF），设置密码
3. `app`：Spring Boot 后端，多阶段构建（Maven 编译 → JRE 运行），环境变量注入数据库/Redis/OSS 配置，暴露 8080
4. `coturn`：TURN 服务，详见 E 节
5. `nginx`：前端静态托管 + 反向代理 + SSL 终结，80 和 443 映射到公网

- 服务间用 compose 网络互通，应用容器通过服务名访问 mysql/redis
- 日志统一 `json-file` 驱动并配置轮转，`max-size: 50m`、`max-file: 5`

## D. 域名、HTTPS 与备案注意事项

- 域名已备案，但若 ECS 也在大陆，需确认网站能正常打开 ICP 备案号（页脚展示备案号并链接到工信部）
- HTTPS 证书方案二选一：
  - **阿里云免费 SSL 证书**（DV 单域名，一年期，控制台直接签发，推荐）
  - 或 Let's Encrypt + certbot 自动续期
- Nginx 配置要点：
  - 全站 HTTPS，`server` 块把 80 全量 301 → 443
  - 反向代理 `/api/**` 到 app 容器；`/ws/**` 需配置 `proxy_set_header Upgrade $http_upgrade` 和 `Connection "upgrade"`（WebSocket 升级必须，否则 H5 连不上信令）
  - H5 静态文件开启 gzip、`Cache-Control: max-age=1d`（index.html 不缓存）、HTTP/2
  - 上传接口 `client_max_body_size 20m;`（视频/大图）
- 提醒：域名访问在浏览器出现"不安全"、或 WebSocket 被拦，优先排查证书与 `wss://` 是否走了 443

## E. coturn（TURN 中继）部署

- 用 `coturn/coturn` 官方镜像，配置要点：
  - 公网地址、端口 3478，`listening-ip` 与 `relay-ip` 设为容器/宿主机内网 IP，`external-ip` 填 ECS 公网 IP
  - 启用时间受限凭据：`use-auth-secret` + `static-auth-secret`（后端 HMAC 生成短期凭据），或用 REST API 方式
  - 放行 UDP/TCP 3478，阿里云安全组 **同时** 放行（安全组是白名单，云服务商层拦截在系统防火墙之前）
  - 自测命令：`turnutils_uclient` 或在线 TURN 测试工具，确认 relay 路径可用
- 若发现 P2P 打洞不成功，优先检查：安全组 3478 是否放行、`external-ip` 是否正确、凭据是否过期

## F. 文件存储：OSS 优先

- 图片/视频/语音消息统一走**阿里云 OSS**（Bucket 私有读写 + CDN 加速域名可选），后端只存 OSS Key，上传用后端签名的直传 URL（STS 临时凭证），避免流量经过 ECS
- 若暂不用 OSS，本地磁盘方案也要预留 `StorageService` 接口抽象，上线初期本地、后期平滑切换 OSS
- 图片上传后立即生成缩略图（尺寸：头像 200×200、朋友圈 640×640、原图保留），减少信息流流量

## G. 国内合规硬性要求（重要，阿里云大陆服务器必须满足）

1. **内容审核**：用户发布的文字/图片必须过审核后才能公开可见（可用阿里云内容安全 API 或开源的敏感词库），设"先审后发"或"先发后审+定时巡检"
2. **实名认证**：注册需手机号实名（阿里云短信服务发送验证码），这是工信部/网信办要求
3. **数据合规**：用户个人信息（手机号等）加密存储，提供注销账号能力；隐私政策页面必须有
4. **日志留存**：用户操作日志、登录日志保留至少 180 天
5. **举报与封禁**：动态举报接口 + 管理端封禁能力
6. **备案信息展示**：网站首页页脚展示 ICP 备案号并链接到 beian.miit.gov.cn

## H. 上线前检查清单（DeepSeek 应逐项给出可执行方案）

- [ ] HTTPS 生效，WSS 能连上（浏览器控制台无混合内容报错）
- [ ] 安全组/防火墙只开放必要端口，SSH 加固（改端口或密钥登录）
- [ ] 数据库定时备份（每日 mysqldump 或阿里云 RDS 自动备份），备份保留 7 天
- [ ] 容器 `restart: always`，进程守护（ECS 意外重启后自动拉起）
- [ ] 日志集中查看与按天分割（Nginx access/error log、app log 按天切分）
- [ ] 慢查询开启与说明（`slow_query_log`），Redis 设置 maxmemory
- [ ] 提供一份 `deploy.sh` + `.env` 示例：一条命令完成 `docker compose up -d`
- [ ] 提供 Nginx 完整配置（含 gzip、静态缓存、HTTP/2、WebSocket upgrade）
- [ ] 内存/CPU 监控方案（可用 ECS 自带云监控或 docker stats 脚本）

---

# 交付要求

请分以下阶段逐步实现并输出（每阶段完成代码 + 简短说明）：

1. **阶段一：项目骨架 + 数据库建表脚本 + 登录注册 + 基础框架搭建**
2. **阶段二：朋友圈核心功能**（发帖、信息流、点赞、评论、转发）
3. **阶段三：即时聊天**（会话、消息、WebSocket 推送、离线补偿）
4. **阶段四：音视频通话**（信令服务 + WebRTC 前端实现 + TURN 降级）
5. **阶段五：UI 打磨 + 动效 + 暗色主题 + 性能优化**
6. **阶段六：生产环境部署** —— 按上述部署方案输出 `docker-compose.yml`、`deploy.sh`、Nginx 全量配置、coturn 配置、SSL 证书获取脚本、.env 示例、环境变量说明
7. **阶段七：合规与运维** —— 内容审核接入、实名认证、日志留存、备份脚本、监控方案、上线检查清单逐项落地

每个阶段优先交付可运行的最小闭环，再逐步扩展。请直接输出可落地的完整代码，而不是只给方案或伪代码。若某项需求有更优实现方案，请说明理由后给出你的推荐。

部署相关输出要求：直接给出可复制运行的配置文件和命令，并标注每步在阿里云控制台的操作位置；部署命令要能在干净的 Ubuntu 22.04 上直接执行成功。
