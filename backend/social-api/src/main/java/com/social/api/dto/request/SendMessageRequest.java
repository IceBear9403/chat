package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送聊天消息请求。
 */
@Data
public class SendMessageRequest {

    @NotNull(message = "接收人不能为空")
    private Long receiverId;

    /** 类型：1 文本，2 图片，3 语音，4 视频，5 通话 */
    @NotNull(message = "消息类型不能为空")
    private Integer msgType;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息最长 2000 字")
    private String content;

    /** 客户端幂等 ID（可选，同发送方同 ID 不重复入库） */
    @Size(max = 64, message = "clientMsgId 过长")
    private String clientMsgId;
}
