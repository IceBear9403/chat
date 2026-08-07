package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关系表：好友 / 关注。
 */
@Data
@TableName("user_relation")
public class UserRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主动方用户 id */
    private Long userId;

    /** 目标用户 id */
    private Long targetUserId;

    /** 关系类型：1 好友申请，2 关注 */
    private Integer relationType;

    /** 状态：0 待处理，1 已同意，2 已拒绝，3 已解除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
