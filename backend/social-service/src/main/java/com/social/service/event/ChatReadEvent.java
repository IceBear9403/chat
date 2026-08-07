package com.social.service.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消息已读事件。推送给对端，用于已读回执。
 */
@Getter
@RequiredArgsConstructor
public class ChatReadEvent {

    private final Long conversationId;

    /** 谁读了（本人） */
    private final Long readerId;

    /** 对端（接收 READ 推送的一方） */
    private final Long peerId;
}
