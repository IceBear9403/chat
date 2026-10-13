package com.social.service.dto;

import lombok.Data;

/**
 * 认证配置（供前端登录/注册页渲染）。authMode: phone=手机验证，email=邮箱验证。
 */
@Data
public class AuthConfigVO {

    private String authMode;
}
