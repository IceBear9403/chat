package com.social.service.dto;

import lombok.Data;

/**
 * 用户信息（对外）。手机号脱敏。
 */
@Data
public class UserVO {

    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String signature;
    private Integer gender;
    private String region;
}
