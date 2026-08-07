package com.social.service.event;

import com.social.service.dto.MessageVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消息已入库事件。由 social-websocket 监听并通过 STOMP 推送双方。
 */
@Getter
@RequiredArgsConstructor
public class ChatMessageSentEvent {

    private final MessageVO message;

    /** 发送方在该会话的未读数 */
    private final long senderUnread;

    /** 接收方在该会话的未读数 */
    private final long receiverUnread;
}
