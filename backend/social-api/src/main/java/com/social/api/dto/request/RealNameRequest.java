package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 实名认证请求（阶段七合规）。
 */
@Data
public class RealNameRequest {

    @NotBlank(message = "请输入真实姓名")
    @Size(max = 20, message = "真实姓名最长 20 个字符")
    private String realName;

    @NotBlank(message = "请输入身份证号")
    @Pattern(regexp = "^\\d{17}[0-9Xx]$", message = "请输入 18 位身份证号")
    private String idCard;
}
