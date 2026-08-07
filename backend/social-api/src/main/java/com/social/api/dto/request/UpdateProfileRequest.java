package com.social.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;

    private String avatar;

    @Size(max = 100, message = "个性签名最长 100 个字符")
    private String signature;

    @Min(value = 0, message = "性别参数不正确")
    @Max(value = 2, message = "性别参数不正确")
    private Integer gender;

    @Size(max = 50, message = "地区最长 50 个字符")
    private String region;
}
