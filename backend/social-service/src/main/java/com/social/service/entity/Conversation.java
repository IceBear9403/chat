package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（双人）。user_a &lt; user_b，唯一键保证同一对用户只有一条会话。
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 较小用户 ID */
    private Long userA;

    /** 较大用户 ID */
    private Long userB;

    /** 最后一条消息 id */
    private Long lastMessageId;

    private LocalDateTime lastMessageTime;

    /** 会话列表预览（文本截断 / 资源类型描述） */
    private String lastMessagePreview;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
