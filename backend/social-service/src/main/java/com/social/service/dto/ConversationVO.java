package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（对外）。peer 为对方用户，lastMessage 为空表示尚未聊天。
 */
@Data
public class ConversationVO {

    private Long id;
    private Long peerId;
    private UserVO peer;
    private MessageVO lastMessage;
    /** 我方未读数 */
    private Long unreadCount;
    /** 对方是否在线（Redis 在线状态） */
    private Boolean peerOnline;
    private LocalDateTime lastMessageTime;
    /** 我是否已删除该会话 */
    private Boolean deleted;
}
