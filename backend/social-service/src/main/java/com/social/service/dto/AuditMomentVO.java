package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端：内容审核队列项（待审核动态 + 作者信息 + 媒体）。
 */
@Data
public class AuditMomentVO {

    private Long id;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String content;
    private Integer visibility;
    private List<MomentMediaVO> media;
    private LocalDateTime createdAt;
}
