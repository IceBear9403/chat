package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 朋友圈动态。
 */
@Data
@TableName("moment")
public class Moment {

    /** 类型：0 图文，1 纯文字，2 转发 */
    public static final int TYPE_IMAGE_TEXT = 0;
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_REPOST = 2;

    /** 可见范围：0 公开，1 仅好友，2 私密 */
    public static final int VISIBILITY_PUBLIC = 0;
    public static final int VISIBILITY_FRIENDS = 1;
    public static final int VISIBILITY_PRIVATE = 2;

    /** 状态：0 待审核，1 已发布，2 审核不通过，3 已删除 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_DELETED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者 */
    private Long userId;

    /** 正文 */
    private String content;

    /** 类型：0 图文，1 纯文字，2 转发 */
    private Integer type;

    /** 可见范围：0 公开，1 仅好友，2 私密 */
    private Integer visibility;

    /** 转发来源动态 id（type=2 时使用） */
    private Long repostFrom;

    /** 状态：0 待审核，1 已发布，2 审核不通过，3 已删除 */
    private Integer status;

    private Integer likeCount;
    private Integer commentCount;
    private Integer repostCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
