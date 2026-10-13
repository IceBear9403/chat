package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。account 为当前认证模式下的账号：手机验证模式=手机号，邮箱验证模式=邮箱。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "手机号/邮箱不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    private String password;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为 6 位数字")
    private String code;

    /** 可选：昵称，默认"用户+手机尾号"或"用户+邮箱前缀" */
    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;
}
