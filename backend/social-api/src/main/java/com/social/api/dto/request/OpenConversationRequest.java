package com.social.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 打开会话请求（与某用户发起聊天）。
 */
@Data
public class OpenConversationRequest {

    @NotNull(message = "对方用户不能为空")
    private Long peerId;
}
