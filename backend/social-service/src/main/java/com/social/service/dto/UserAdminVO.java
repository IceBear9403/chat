package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端：用户列表项。
 */
@Data
public class UserAdminVO {

    private Long id;
    /** 手机号（脱敏；邮箱模式用户可能为空） */
    private String phone;
    /** 邮箱（脱敏；手机模式用户可能为空） */
    private String email;
    private String nickname;
    private String avatar;
    /** 状态：0 正常，1 封禁，2 注销 */
    private Integer status;
    /** 实名状态：0 未实名，1 已实名 */
    private Integer realnameStatus;
    private Boolean isAdmin;
    private LocalDateTime createdAt;
}
