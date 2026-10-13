package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码。account 为当前认证模式下的账号：手机验证模式=手机号，邮箱验证模式=邮箱。
 */
@Data
public class SendCodeRequest {

    @NotBlank(message = "手机号/邮箱不能为空")
    private String account;

    /** 场景：register / login / resetPassword */
    @NotBlank(message = "场景不能为空")
    private String scene;
}
