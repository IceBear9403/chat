import http from './http'
import type { ChatHistoryVO, ConversationVO, MessageVO } from '@/types'

export interface SendMessageParams {
  receiverId: number
  /** 1 文本，2 图片，3 语音，4 视频，5 通话 */
  msgType: number
  content: string
  /** 客户端幂等 ID（重试不重复入库） */
  clientMsgId?: string
}

export const chatApi = {
  send: (payload: SendMessageParams): Promise<MessageVO> =>
    http.post<MessageVO, MessageVO>('/chat/send', payload),

  openConversation: (peerId: number): Promise<ConversationVO> =>
    http.post<ConversationVO, ConversationVO>('/chat/conversation', { peerId }),

  conversations: (): Promise<ConversationVO[]> =>
    http.get<ConversationVO[], ConversationVO[]>('/chat/conversations'),

  conversationDetail: (conversationId: number): Promise<ConversationVO> =>
    http.get<ConversationVO, ConversationVO>(`/chat/${conversationId}`),

  messages: (conversationId: number, cursorId?: number | null, size = 20): Promise<ChatHistoryVO> =>
    http.get<ChatHistoryVO, ChatHistoryVO>(`/chat/${conversationId}/messages`, {
      params: { cursorId: cursorId ?? undefined, size },
    }),

  read: (conversationId: number): Promise<null> =>
    http.post<null, null>(`/chat/${conversationId}/read`),

  /** 删除会话（软删，对方发新消息后重新可见） */
  deleteConversation: (conversationId: number): Promise<null> =>
    http.delete<null, null>(`/chat/${conversationId}`),
}
