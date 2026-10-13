package com.social.service.dto;

import lombok.Data;

/**
 * 用户信息（对外）。手机号脱敏。
 */
@Data
public class UserVO {

    private Long id;
    /** 手机号（脱敏；邮箱模式用户可能为空） */
    private String phone;
    /** 邮箱（脱敏；手机模式用户可能为空） */
    private String email;
    private String nickname;
    private String avatar;
    private String signature;
    private Integer gender;
    private String region;
    /** 是否管理员（前端展示管理入口） */
    private Boolean isAdmin;
    /** 实名状态：0 未实名，1 已实名 */
    private Integer realnameStatus;
}
