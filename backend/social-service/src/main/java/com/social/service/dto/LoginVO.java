package com.social.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册返回体：令牌 + 用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private TokenVO token;
    private UserVO user;
}
