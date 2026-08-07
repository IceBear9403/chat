package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.service.dto.ChatHistoryVO;
import com.social.service.dto.ConversationVO;
import com.social.service.dto.MessageVO;
import com.social.service.dto.UserVO;
import com.social.service.entity.Conversation;
import com.social.service.entity.Message;
import com.social.service.entity.User;
import com.social.service.event.ChatMessageSentEvent;
import com.social.service.event.ChatReadEvent;
import com.social.service.mapper.ConversationMapper;
import com.social.service.mapper.MessageMapper;
import com.social.service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 即时聊天业务：会话、消息、已读、在线状态。
 *
 * <p>消息入库后发布 {@link ChatMessageSentEvent}（事务提交后），由 social-websocket 模块
 * 监听并通过 STOMP 实时推送双方。离线补偿由消息落库 + 会话未读计数保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 发送 ====================

    /**
     * 发送消息。clientMsgId 幂等：同发送方同 ID 重复请求返回已存在消息，不重复入库。
     */
    @Transactional
    public MessageVO sendMessage(Long senderId, Message message) {
        User receiver = userMapper.selectById(message.getReceiverId());
        if (receiver == null || receiver.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (message.getMsgType() == null
                || message.getMsgType() < Message.TYPE_TEXT || message.getMsgType() > Message.TYPE_CALL) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息类型不正确");
        }

        // 幂等：同发送方同 clientMsgId 已存在则直接返回，避免网络重试重复发送
        if (message.getClientMsgId() != null) {
            Message dup = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getSenderId, senderId)
                    .eq(Message::getClientMsgId, message.getClientMsgId()));
            if (dup != null) {
                return toVO(dup);
            }
        }

        Conversation conversation = getOrCreateConversation(senderId, message.getReceiverId());
        message.setId(null);
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setStatus(Message.STATUS_SENT);
        messageMapper.insert(message);

        // 更新会话最后一条消息
        Conversation update = new Conversation();
        update.setId(conversation.getId());
        update.setLastMessageId(message.getId());
        update.setLastMessageTime(message.getCreatedAt());
        update.setLastMessagePreview(previewOf(message));
        conversationMapper.updateById(update);

        MessageVO vo = toVO(message);
        long senderUnread = unreadCount(conversation.getId(), senderId);
        long receiverUnread = unreadCount(conversation.getId(), message.getReceiverId());
        publishAfterCommit(() -> eventPublisher.publishEvent(
                new ChatMessageSentEvent(vo, senderUnread, receiverUnread)));
        return vo;
    }

    // ==================== 会话 ====================

    /** 获取或创建与对方用户的会话（并发下依赖唯一键去重）。 */
    public Conversation getOrCreateConversation(Long userId, Long peerId) {
        long a = Math.min(userId, peerId);
        long b = Math.max(userId, peerId);
        Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserA, a)
                .eq(Conversation::getUserB, b));
        if (conv != null) {
            return conv;
        }
        Conversation created = new Conversation();
        created.setUserA(a);
        created.setUserB(b);
        try {
            conversationMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getUserA, a)
                    .eq(Conversation::getUserB, b));
        }
    }

    /** 会话列表（按最后消息时间倒序），含对端信息、未读数、在线状态。 */
    public List<ConversationVO> conversationList(Long userId) {
        List<Conversation> list = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .and(w -> w.eq(Conversation::getUserA, userId)
                        .or()
                        .eq(Conversation::getUserB, userId))
                .isNotNull(Conversation::getLastMessageId)
                .orderByDesc(Conversation::getLastMessageTime));

        List<ConversationVO> result = new ArrayList<>();
        for (Conversation c : list) {
            Long peerId = c.getUserA().equals(userId) ? c.getUserB() : c.getUserA();
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setPeerId(peerId);
            vo.setPeer(peerOf(peerId));
            if (c.getLastMessageId() != null) {
                Message last = messageMapper.selectById(c.getLastMessageId());
                if (last != null) {
                    vo.setLastMessage(toVO(last));
                }
            }
            vo.setUnreadCount(unreadCount(c.getId(), userId));
            vo.setPeerOnline(presenceService.online(peerId));
            vo.setLastMessageTime(c.getLastMessageTime());
            if (vo.getPeer() != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /** 打开（获取或创建）与某用户的会话，返回会话详情。 */
    public ConversationVO openConversation(Long userId, Long peerId) {
        if (peerId.equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能和自己聊天");
        }
        Conversation c = getOrCreateConversation(userId, peerId);
        return conversationDetail(userId, c.getId());
    }

    /** 会话详情（从推送/列表进入聊天窗口时获取对端信息）。 */
    public ConversationVO conversationDetail(Long userId, Long conversationId) {
        Conversation c = getMemberConversation(userId, conversationId);
        Long peerId = c.getUserA().equals(userId) ? c.getUserB() : c.getUserA();
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        vo.setPeerId(peerId);
        vo.setPeer(peerOf(peerId));
        if (c.getLastMessageId() != null) {
            Message last = messageMapper.selectById(c.getLastMessageId());
            if (last != null) {
                vo.setLastMessage(toVO(last));
            }
        }
        vo.setUnreadCount(unreadCount(c.getId(), userId));
        vo.setPeerOnline(presenceService.online(peerId));
        vo.setLastMessageTime(c.getLastMessageTime());
        return vo;
    }

    // ==================== 历史消息 ====================

    /**
     * 拉取会话历史。游标按 id 倒序（id &lt; cursorId），返回升序时间线。
     */
    public ChatHistoryVO messageList(Long userId, Long conversationId, Long cursorId, int size) {
        getMemberConversation(userId, conversationId);
        int limit = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getId)
                .last("limit " + (limit + 1));
        if (cursorId != null) {
            wrapper.lt(Message::getId, cursorId);
        }

        List<Message> rows = messageMapper.selectList(wrapper);
        boolean hasMore = rows.size() > limit;
        List<Message> page = rows.size() > limit ? rows.subList(0, limit) : rows;

        List<MessageVO> records = new ArrayList<>(page.size());
        for (int i = page.size() - 1; i >= 0; i--) {
            records.add(toVO(page.get(i)));
        }
        ChatHistoryVO vo = new ChatHistoryVO();
        vo.setRecords(records);
        vo.setHasMore(hasMore);
        return vo;
    }

    // ==================== 已读 ====================

    /** 标记某会话所有消息已读，并推送已读回执给对端。 */
    @Transactional
    public long markRead(Long userId, Long conversationId) {
        Conversation c = getMemberConversation(userId, conversationId);
        Long peerId = c.getUserA().equals(userId) ? c.getUserB() : c.getUserA();
        long updated = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getStatus, Message.STATUS_SENT)
                .set(Message::getStatus, Message.STATUS_READ));
        if (updated > 0) {
            publishAfterCommit(() -> eventPublisher.publishEvent(new ChatReadEvent(conversationId, userId, peerId)));
        }
        return updated;
    }

    /** 用户在某会话的未读数。 */
    public long unreadCount(Long conversationId, Long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getStatus, Message.STATUS_SENT));
        return count == null ? 0 : count;
    }

    // ==================== 内部方法 ====================

    /** 校验会话存在且当前用户是成员，返回会话。 */
    private Conversation getMemberConversation(Long userId, Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_EXIST);
        }
        if (!c.getUserA().equals(userId) && !c.getUserB().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return c;
    }

    private UserVO peerOf(Long peerId) {
        try {
            return userService.getUserVO(peerId);
        } catch (BusinessException e) {
            log.warn("会话对端用户不存在: userId={}", peerId);
            return null;
        }
    }

    private String previewOf(Message m) {
        return switch (m.getMsgType()) {
            case Message.TYPE_TEXT -> {
                String s = m.getContent() == null ? "" : m.getContent().trim();
                yield s.length() > 60 ? s.substring(0, 60) + "…" : s;
            }
            case Message.TYPE_IMAGE -> "[图片]";
            case Message.TYPE_VOICE -> "[语音]";
            case Message.TYPE_VIDEO -> "[视频]";
            case Message.TYPE_CALL -> "[通话]";
            default -> "";
        };
    }

    private MessageVO toVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setConversationId(m.getConversationId());
        vo.setSenderId(m.getSenderId());
        vo.setReceiverId(m.getReceiverId());
        vo.setMsgType(m.getMsgType());
        vo.setContent(m.getContent());
        vo.setStatus(m.getStatus());
        vo.setClientMsgId(m.getClientMsgId());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    /** 事件在事务提交后再发布，避免回滚导致已推送但未入库。 */
    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
