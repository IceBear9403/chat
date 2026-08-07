package com.social.service.dto;

import lombok.Data;

/**
 * 动态媒体（图片/视频）。
 */
@Data
public class MomentMediaVO {

    private Long id;
    private Integer mediaType;
    private String url;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;
    private Integer sort;
}
