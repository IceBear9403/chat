package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料表，与 user 表 1:1。注册时同步创建。
 */
@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 昵称，注册时默认生成 "用户+手机尾号" */
    private String nickname;

    /** 头像 OSS Key / URL */
    private String avatar;

    private String signature;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    private String region;

    /** 最近一次自行修改昵称的时间（昵称修改间隔限制；管理员/系统改名不计入） */
    private LocalDateTime nicknameUpdatedAt;

    /** 真实姓名（AES-GCM 加密存储，阶段七实名） */
    private String realName;

    /** 身份证号（AES-GCM 加密存储，阶段七实名） */
    private String idCard;

    /** 实名状态：0 未实名，1 已实名 */
    private Integer realnameStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
