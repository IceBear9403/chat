package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号表。密码只存加盐哈希（BCrypt），明文永不落库。
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号（AES-GCM 加密存储，见 PiiCrypto；登录/查重用 phoneHash） */
    private String phone;

    /** 手机号 SHA-256 哈希（唯一，用于按手机号精确检索，避免明文 LIKE） */
    private String phoneHash;

    /** 邮箱（AES-GCM 加密存储，见 PiiCrypto；登录/查重用 emailHash） */
    private String email;

    /** 邮箱 SHA-256 哈希（唯一，按邮箱精确检索，避免明文 LIKE） */
    private String emailHash;

    /** BCrypt 哈希后的密码 */
    private String password;

    /** 是否管理员：0 否，1 是（管理端入口，库内标记） */
    private Integer isAdmin;

    /** 状态：0 正常，1 封禁，2 已注销 */
    private Integer status;

    /** 逻辑删除：0 未删除，1 已删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
