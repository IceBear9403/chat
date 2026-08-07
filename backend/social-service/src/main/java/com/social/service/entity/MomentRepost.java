package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态转发记录（unique(moment_id, user_id) 幂等防重复转发）。
 */
@Data
@TableName("moment_repost")
public class MomentRepost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被转发动态 id */
    private Long momentId;

    /** 转发人 */
    private Long userId;

    /** 转发附带文字 */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
