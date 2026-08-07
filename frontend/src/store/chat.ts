import { defineStore } from 'pinia'
import { chatApi } from '@/api/chat'
import type { ChatPushVO, ConversationVO, MessageVO } from '@/types'
import { connectChat, disconnectChat } from '@/utils/stomp'

/** Chat.vue 注册的实时监听：onMessage 追加消息，onRead 更新已读回执 */
export interface ChatListener {
  conversationId: number
  onMessage: (message: MessageVO) => void
  onRead: () => void
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [] as ConversationVO[],
    connected: false,
    initialized: false,
    /** 正在查看的会话 → 监听器（会话页挂载时注册） */
    listeners: {} as Record<number, ChatListener>,
  }),

  getters: {
    unreadTotal: (state) =>
      state.conversations.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0),
  },

  actions: {
    /** 连接 STOMP（幂等）并在连接成功后拉取会话。 */
    init() {
      if (this.initialized) {
        return
      }
      this.initialized = true
      connectChat({
        onMessage: (payload) => this.handlePush(payload),
        onConnect: () => {
          this.connected = true
          this.refresh()
        },
        onDisconnect: () => {
          this.connected = false
        },
      })
    },

    async refresh() {
      try {
        this.conversations = await chatApi.conversations()
      } catch {
        // 拉取失败静默，下次推送/进页重试
      }
    },

    async ensureConnected() {
      if (!this.initialized) {
        this.init()
      }
    },

    /** 实时推送分发：更新会话列表/未读，并路由到正在查看的聊天窗口 */
    handlePush(payload: ChatPushVO) {
      if (payload.type === 'READ') {
        const listener = this.listeners[payload.conversationId]
        listener?.onRead()
        return
      }
      if (payload.type !== 'MESSAGE' || !payload.message) {
        return
      }
      const message = payload.message
      const conv = this.conversations.find((c) => c.id === payload.conversationId)
      const listening = !!this.listeners[payload.conversationId]

      if (conv) {
        conv.lastMessage = message
        conv.lastMessageTime = message.createdAt
        if (listening) {
          // 正在查看该会话：消息即时已读
          conv.unreadCount = 0
          this.listeners[payload.conversationId].onMessage(message)
          chatApi.read(payload.conversationId).catch(() => {})
        } else {
          conv.unreadCount = payload.unreadCount
        }
      } else {
        // 新会话（此前无记录）
        this.refresh()
      }
    },

    setListener(listener: ChatListener) {
      this.listeners[listener.conversationId] = listener
    },

    clearListener(conversationId: number) {
      delete this.listeners[conversationId]
    },

    logout() {
      disconnectChat()
      this.initialized = false
      this.connected = false
      this.conversations = []
      this.listeners = {}
    },
  },
})
