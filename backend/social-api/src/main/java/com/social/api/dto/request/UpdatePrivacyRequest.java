package com.social.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdatePrivacyRequest {

    /** 0 公开，1 仅好友，2 私密 */
    @Min(value = 0, message = "可见范围参数不正确")
    @Max(value = 2, message = "可见范围参数不正确")
    private Integer momentsVisible;

    /** 0 不允许，1 允许 */
    @Min(value = 0, message = "参数不正确")
    @Max(value = 1, message = "参数不正确")
    private Integer allowStrangerReply;

    /** 0 所有人，1 仅好友，2 不允许 */
    @Min(value = 0, message = "私聊策略参数不正确")
    @Max(value = 2, message = "私聊策略参数不正确")
    private Integer chatPolicy;
}
