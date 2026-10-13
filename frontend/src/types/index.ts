/** 后端统一返回结构：{ code, message, data }，code=0 表示成功 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/** 令牌 */
export interface TokenVO {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
}

/** 用户信息（手机号/邮箱已脱敏；邮箱模式用户 phone 为空，手机模式用户 email 为空） */
export interface UserVO {
  id: number
  phone: string
  email?: string
  nickname: string
  avatar: string | null
  signature: string | null
  gender: number
  region: string | null
  /** 是否管理员（阶段七） */
  isAdmin: boolean
  /** 实名状态：0 未实名，1 已实名 */
  realnameStatus: number
}

/** 登录/注册返回 */
export interface LoginVO {
  token: TokenVO
  user: UserVO
}

/** 认证配置：phone=手机验证，email=邮箱验证（由后端 app.auth.mode 决定） */
export interface AuthConfigVO {
  authMode: 'phone' | 'email'
}

/** 隐私设置 */
export interface FeedRuleVO {
  id: number
  userId: number
  momentsVisible: number
  allowStrangerReply: number
  chatPolicy: number
}

/** 动态作者 */
export interface MomentAuthorVO {
  userId: number
  nickname: string
  avatar: string | null
}

/** 动态媒体 */
export interface MomentMediaVO {
  id: number
  mediaType: number
  url: string
  thumbnailUrl: string | null
  width: number | null
  height: number | null
  sort: number
}

/** 动态 */
export interface MomentVO {
  id: number
  author: MomentAuthorVO
  type: number
  content: string | null
  visibility: number
  /** 状态：0 待审核，1 已发布，2 审核不通过，3 已删除（阶段七先发后审） */
  status?: number
  media: MomentMediaVO[]
  likeCount: number
  commentCount: number
  repostCount: number
  liked: boolean
  repostFrom: MomentVO | null
  createdAt: string
}

/** 动态评论 */
export interface MomentCommentVO {
  id: number
  momentId: number
  author: MomentAuthorVO
  content: string
  parentId: number | null
  replyToName: string | null
  createdAt: string
}

/** 点赞结果 */
export interface LikeVO {
  liked: boolean
  likeCount: number
}

/** 信息流分页 */
export interface FeedPageVO {
  records: MomentVO[]
  hasMore: boolean
  nextCursorTime: string | null
  nextCursorId: number | null
}

/** 动态详情 */
export interface MomentDetailVO {
  moment: MomentVO
  comments: MomentCommentVO[]
  likePreview: MomentAuthorVO[]
}

/** 文件上传返回 */
export interface FileUploadVO {
  url: string
  originalName: string
  size: number
}

/** 聊天消息 */
export interface MessageVO {
  id: number
  conversationId: number
  senderId: number
  receiverId: number
  /** 类型：1 文本，2 图片，3 语音，4 视频，5 通话 */
  msgType: number
  content: string
  /** 状态：0 已发送（未读），1 已读 */
  status: number
  clientMsgId: string | null
  createdAt: string
}

/** 会话 */
export interface ConversationVO {
  id: number
  peerId: number
  peer: UserVO
  lastMessage: MessageVO | null
  unreadCount: number
  peerOnline: boolean
  lastMessageTime: string | null
}

/** WebSocket 定向推送载荷 */
export interface ChatPushVO {
  type: 'MESSAGE' | 'READ'
  message: MessageVO | null
  conversationId: number
  unreadCount: number
}

/** 聊天历史分页 */
export interface ChatHistoryVO {
  records: MessageVO[]
  hasMore: boolean
}

/** 通话信令（STOMP /app/call 上行、/user/queue/call 下行） */
export interface CallSignalVO {
  /** invite / accept / reject / cancel / offer / answer / candidate / end / busy / offline */
  action: string
  /** 全局呼叫 ID */
  callId: string | null
  /** 1 语音，2 视频 */
  callType: number | null
  callerId: number | null
  calleeId: number | null
  from: number | null
  to: number | null
  /** offer / answer 的 SDP */
  sdp: string | null
  /** ICE candidate（JSON 字符串） */
  candidate: string | null
  /** reject / end / busy / offline 原因 */
  reason: string | null
  /** 对方昵称 / 头像（invite 时服务端回填主叫信息） */
  peerName: string | null
  peerAvatar: string | null
}

/** ICE 服务器 */
export interface IceServerVO {
  urls: string
  username?: string
  credential?: string
}

/** WebRTC ICE 配置（GET /api/call/config） */
export interface CallConfigVO {
  iceServers: IceServerVO[]
}

/** 业务错误 */
export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

// ---------- 阶段七：管理端 / 合规 ----------

/** 管理端通用分页（无游标，手动 LIMIT 分页） */
export interface PageVO<T> {
  records: T[]
  page: number
  size: number
  total: number
  hasMore: boolean
}

/** 管理端：用户列表项 */
export interface UserAdminVO {
  id: number
  /** 手机号（脱敏；邮箱模式用户为空） */
  phone: string
  /** 邮箱（脱敏；手机模式用户为空） */
  email?: string
  nickname: string | null
  avatar: string | null
  /** 状态：0 正常，1 封禁，2 注销 */
  status: number
  /** 实名状态：0 未实名，1 已实名 */
  realnameStatus: number
  isAdmin: boolean
  createdAt: string
}

/** 管理端：内容审核队列项 */
export interface AuditMomentVO {
  id: number
  authorId: number
  authorNickname: string | null
  authorAvatar: string | null
  content: string | null
  visibility: number
  media: MomentMediaVO[]
  createdAt: string
}

/** 管理端：举报队列项 */
export interface ReportAdminVO {
  id: number
  momentId: number
  momentContent: string | null
  momentAuthorNickname: string | null
  reporterNickname: string | null
  reason: string | null
  handleStatus: number
  createdAt: string
}

/** 管理端：操作日志项 */
export interface UserLogVO {
  id: number
  userId: number
  action: string
  detail: string | null
  ip: string | null
  createdAt: string
}
