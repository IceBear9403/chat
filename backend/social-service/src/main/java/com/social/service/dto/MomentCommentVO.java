package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论（含作者、被回复人昵称）。
 */
@Data
public class MomentCommentVO {

    private Long id;
    private Long momentId;
    private MomentAuthorVO author;
    private String content;
    private Long parentId;
    /** 楼中楼被回复人昵称 */
    private String replyToName;
    private LocalDateTime createdAt;
}
