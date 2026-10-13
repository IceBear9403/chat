package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端：举报队列项。
 */
@Data
public class ReportAdminVO {

    private Long id;
    private Long momentId;
    /** 被举报动态内容（前 100 字） */
    private String momentContent;
    /** 被举报动态作者昵称 */
    private String momentAuthorNickname;
    /** 举报人昵称 */
    private String reporterNickname;
    private String reason;
    /** 处理状态：0 待处理，1 已处理 */
    private Integer handleStatus;
    private LocalDateTime createdAt;
}
