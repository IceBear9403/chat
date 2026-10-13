package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户操作日志（阶段七合规：日志留存 ≥180 天，由定时任务清理）。
 */
@Data
@TableName("user_log")
public class UserLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID */
    private Long userId;

    /** 动作：register / login / loginCode / logout / realname / deleteAccount / ban / unban / audit / report ... */
    private String action;

    /** 详情（如：手机号尾号、被操作对象 id） */
    private String detail;

    /** 客户端 IP（经 X-Forwarded-For 取首个） */
    private String ip;

    /** User-Agent */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
