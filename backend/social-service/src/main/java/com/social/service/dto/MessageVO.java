package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息（对外）。
 */
@Data
public class MessageVO {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    /** 类型：1 文本，2 图片，3 语音，4 视频，5 通话 */
    private Integer msgType;
    private String content;
    /** 状态：0 已发送（未读），1 已读 */
    private Integer status;
    /** 客户端幂等 ID（推送回显用于去重） */
    private String clientMsgId;
    private LocalDateTime createdAt;
}
