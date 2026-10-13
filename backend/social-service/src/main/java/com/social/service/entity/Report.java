package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态举报（阶段七合规：举报受理 + 管理端处理）。
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被举报动态 ID */
    private Long momentId;

    /** 举报人 ID */
    private Long reporterId;

    /** 举报理由（可选） */
    private String reason;

    /** 处理状态：0待处理 1已处理 */
    private Integer handleStatus;

    /** 处理人（管理员）ID */
    private Long handledBy;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
