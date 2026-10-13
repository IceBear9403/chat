package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端：操作日志项。
 */
@Data
public class UserLogVO {

    private Long id;
    private Long userId;
    private String action;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
