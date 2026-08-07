package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通话记录（call_record）。
 */
@Data
@TableName("call_record")
public class CallRecord {

    /** 通话类型：1 语音，2 视频 */
    public static final int TYPE_VOICE = 1;
    public static final int TYPE_VIDEO = 2;

    /** 状态：0 呼叫中，1 已接通，2 已拒绝，3 未接，4 挂断，5 掉线 */
    public static final int STATUS_RINGING = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_MISSED = 3;
    public static final int STATUS_HANGUP = 4;
    public static final int STATUS_DROPPED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 信令全局呼叫 ID */
    private String callId;

    private Long callerId;

    private Long calleeId;

    /** 1 语音，2 视频 */
    private Integer callType;

    /** 0 呼叫中，1 已接通，2 已拒绝，3 未接，4 挂断，5 掉线 */
    private Integer status;

    /** 通话时长（秒） */
    private Integer duration;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
