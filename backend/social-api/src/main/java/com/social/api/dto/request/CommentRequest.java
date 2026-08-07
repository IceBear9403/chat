package com.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论请求。
 */
@Data
public class CommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最长 500 字")
    private String content;

    /** 被回复评论 id（楼中楼时必填） */
    private Long parentId;

    /** 被回复用户 id（可选，默认取被回复评论的作者） */
    private Long replyTo;
}
