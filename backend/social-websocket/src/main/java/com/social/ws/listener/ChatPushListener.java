package com.social.ws.listener;

import com.social.service.dto.ChatPushVO;
import com.social.service.dto.MessageVO;
import com.social.service.event.ChatMessageSentEvent;
import com.social.service.event.ChatReadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 聊天事件 → STOMP 定向推送。
 *
 * <p>新消息推给双方（发送方多端同步 + 接收方实时到达），已读回执推给对端。
 * 离线用户无订阅，消息由落库 + 会话未读计数在下次拉取时补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPushListener {

    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onMessageSent(ChatMessageSentEvent event) {
        MessageVO m = event.getMessage();
        push(m.getSenderId(), ChatPushVO.message(m, m.getConversationId(), event.getSenderUnread()));
        push(m.getReceiverId(), ChatPushVO.message(m, m.getConversationId(), event.getReceiverUnread()));
    }

    @EventListener
    public void onRead(ChatReadEvent event) {
        push(event.getPeerId(), ChatPushVO.read(event.getConversationId()));
    }

    private void push(Long userId, ChatPushVO vo) {
        try {
            messaging.convertAndSendToUser(String.valueOf(userId), "/queue/messages", vo);
        } catch (Exception e) {
            log.warn("聊天推送失败 userId={}, type={}", userId, vo.getType(), e);
        }
    }
}
