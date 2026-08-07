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

/** 用户信息（手机号已脱敏） */
export interface UserVO {
  id: number
  phone: string
  nickname: string
  avatar: string | null
  signature: string | null
  gender: number
  region: string | null
}

/** 登录/注册返回 */
export interface LoginVO {
  token: TokenVO
  user: UserVO
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
