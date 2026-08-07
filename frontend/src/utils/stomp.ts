import { Client, type IMessage } from '@stomp/stompjs'
import type { CallSignalVO, ChatPushVO } from '@/types'
import { getAccessToken } from '@/utils/token'

/**
 * STOMP 连接管理（单例）。
 *
 * <ul>
 *   <li>原生 WebSocket 连 {@code /ws}，token 放查询参数（浏览器 WS 无法自定义 Header）</li>
 *   <li>登录订阅 {@code /user/queue/messages}（聊天）与 {@code /user/queue/call}（通话信令）</li>
 *   <li>断线自动重连，重连时读取最新 token（http 拦截器已做 401 静默刷新）</li>
 * </ul>
 *
 * 各 store 通过 connectChat 注册各自回调：ChatStore 处理 onMessage，CallStore 处理
 * onCallSignal。回调以模块变量保存，消息到达时才读取，后注册的不会覆盖先注册的。
 */

let client: Client | null = null
let subscribed = false
let onMessageHandler: ((payload: ChatPushVO) => void) | undefined
let onCallSignalHandler: ((sig: CallSignalVO) => void) | undefined
let onConnectHandler: (() => void) | undefined
let onDisconnectHandler: (() => void) | undefined

export function isConnected(): boolean {
  return client?.connected ?? false
}

/** 连接（幂等）。断线后由 stompjs 按 reconnectDelay 自动重连。 */
export function connectChat(opts: {
  onMessage?: (payload: ChatPushVO) => void
  onCallSignal?: (sig: CallSignalVO) => void
  onConnect?: () => void
  onDisconnect?: () => void
}): void {
  if (opts.onMessage !== undefined) onMessageHandler = opts.onMessage
  if (opts.onCallSignal !== undefined) onCallSignalHandler = opts.onCallSignal
  if (opts.onConnect !== undefined) onConnectHandler = opts.onConnect
  if (opts.onDisconnect !== undefined) onDisconnectHandler = opts.onDisconnect

  if (client) {
    return
  }
  const token = getAccessToken()
  if (!token) {
    return
  }

  client = new Client({
    // 每次连接/重连读取最新 token
    webSocketFactory: () =>
      new WebSocket(`/ws?token=${encodeURIComponent(getAccessToken())}`),
    reconnectDelay: 5000,
    heartbeatIncoming: 15000,
    heartbeatOutgoing: 15000,
    onConnect: () => {
      if (!subscribed) {
        subscribed = true
        client?.subscribe('/user/queue/messages', (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as ChatPushVO
            onMessageHandler?.(payload)
          } catch {
            // 忽略非法载荷
          }
        })
        client?.subscribe('/user/queue/call', (message: IMessage) => {
          try {
            const sig = JSON.parse(message.body) as CallSignalVO
            onCallSignalHandler?.(sig)
          } catch {
            // 忽略非法载荷
          }
        })
      }
      onConnectHandler?.()
    },
    onDisconnect: () => {
      // 优雅断开（DISCONNECT/登出）
      subscribed = false
      onDisconnectHandler?.()
    },
    onWebSocketClose: () => {
      // 关键：意外断线（网络抖动 / 服务端重启）只触发 onWebSocketClose，不触发 onDisconnect。
      // STOMP 会话独立，重连必须重建 SUBSCRIBE；不复位标记会导致连接"看似在线"
      // 却收不到任何 /user/queue/* 推送（表现为"接通后主叫还在拨号界面""收不到邀请"）。
      subscribed = false
    },
    onStompError: () => {
      // 协议层错误：重置订阅标记并断开，稍后带最新 token 重建连接（token 由 http 拦截器静默刷新）
      subscribed = false
      client?.deactivate()
      client = null
      window.setTimeout(() => {
        const token = getAccessToken()
        if (token) {
          connectChat({})
        }
      }, 3000)
    },
  })
  client.activate()
}

/** 发布通话信令到 /app/call。未连接时静默丢弃（由发起方状态兜底）。 */
export function publishSignal(sig: Partial<CallSignalVO>): void {
  if (client?.connected) {
    client.publish({ destination: '/app/call', body: JSON.stringify(sig) })
  }
}

/** 主动断开（登出时调用）。 */
export function disconnectChat(): void {
  if (client) {
    client.deactivate()
    client = null
  }
  subscribed = false
  onMessageHandler = undefined
  onCallSignalHandler = undefined
  onConnectHandler = undefined
  onDisconnectHandler = undefined
}
