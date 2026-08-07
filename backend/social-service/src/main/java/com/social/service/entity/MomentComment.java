package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论（parent_id 支持楼中楼，reply_to 记录被回复用户）。
 */
@Data
@TableName("moment_comment")
public class MomentComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long momentId;

    private Long userId;

    /** 被回复评论 id（null 为顶级评论） */
    private Long parentId;

    /** 被回复用户 id（仅楼中楼时使用） */
    private Long replyTo;

    private String content;

    /** 0 正常，1 已删除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
