package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 验证码登录请求。account 为当前认证模式下的账号：手机验证模式=手机号，邮箱验证模式=邮箱。
 */
@Data
public class CodeLoginRequest {

    @NotBlank(message = "手机号/邮箱不能为空")
    private String account;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为 6 位数字")
    private String code;
}
