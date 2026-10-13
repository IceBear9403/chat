package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注销账号请求（阶段七数据合规）：需二次输入密码确认。
 */
@Data
public class DeleteAccountRequest {

    @NotBlank(message = "请输入密码确认注销")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    private String password;
}
