package com.social.service.dto;

import lombok.Data;

/**
 * WebSocket 定向推送载荷（/user/{id}/queue/messages）。
 *
 * <ul>
 *   <li>type=MESSAGE：新消息，unreadCount 为该用户当前会话未读数</li>
 *   <li>type=READ：对方已读回执</li>
 * </ul>
 */
@Data
public class ChatPushVO {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_READ = "READ";

    private String type;
    private MessageVO message;
    private Long conversationId;
    private Long unreadCount;

    public static ChatPushVO message(MessageVO message, Long conversationId, Long unreadCount) {
        ChatPushVO vo = new ChatPushVO();
        vo.setType(TYPE_MESSAGE);
        vo.setMessage(message);
        vo.setConversationId(conversationId);
        vo.setUnreadCount(unreadCount);
        return vo;
    }

    public static ChatPushVO read(Long conversationId) {
        ChatPushVO vo = new ChatPushVO();
        vo.setType(TYPE_READ);
        vo.setConversationId(conversationId);
        return vo;
    }
}
