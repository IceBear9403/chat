package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息。
 */
@Data
@TableName("message")
public class Message {

    /** 消息类型：1 文本，2 图片，3 语音，4 视频，5 通话 */
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_VOICE = 3;
    public static final int TYPE_VIDEO = 4;
    public static final int TYPE_CALL = 5;

    /** 状态：0 已发送（未读），1 已读 */
    public static final int STATUS_SENT = 0;
    public static final int STATUS_READ = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    /** 类型：1 文本，2 图片，3 语音，4 视频，5 通话 */
    private Integer msgType;

    /** 文本内容 / 资源 URL */
    private String content;

    /** 客户端生成幂等 ID（防重复发送） */
    private String clientMsgId;

    /** 状态：0 已发送（未读），1 已读 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
