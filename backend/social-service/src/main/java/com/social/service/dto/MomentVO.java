package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态详情（信息流与详情页共用）。
 * type=2（转发）时 repostFrom 为原始动态摘要。
 */
@Data
public class MomentVO {

    private Long id;
    private MomentAuthorVO author;
    private Integer type;
    private String content;
    private Integer visibility;
    private List<MomentMediaVO> media;
    private Integer likeCount;
    private Integer commentCount;
    private Integer repostCount;
    /** 当前用户是否已点赞 */
    private Boolean liked;
    /** 转发来源动态（type=2 时非空） */
    private MomentVO repostFrom;
    private LocalDateTime createdAt;
}
