package com.social.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 动态详情：动态 + 评论列表 + 点赞预览。
 */
@Data
public class MomentDetailVO {

    private MomentVO moment;
    private List<MomentCommentVO> comments;
    /** 最近点赞用户预览（最多 5 个） */
    private List<MomentAuthorVO> likePreview;
}
