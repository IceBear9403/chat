package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 隐私设置表（每用户一行，注册时初始化默认值）。
 */
@Data
@TableName("feed_rule")
public class FeedRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 朋友圈可见范围：0 公开，1 仅好友，2 私密 */
    private Integer momentsVisible;

    /** 允许非好友评论我的动态：0 否，1 是 */
    private Integer allowStrangerReply;

    /** 私聊策略：0 所有人，1 仅好友，2 不允许 */
    private Integer chatPolicy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
