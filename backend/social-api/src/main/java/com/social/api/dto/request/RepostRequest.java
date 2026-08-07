package com.social.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转发动态请求。
 */
@Data
public class RepostRequest {

    /** 转发附带文字（选填） */
    @Size(max = 200, message = "转发文字最长 200 字")
    private String content;

    /** 转发后可见范围：0 公开，1 仅好友，2 私密（默认公开） */
    private Integer visibility;
}
