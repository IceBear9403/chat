package com.social.service.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态媒体（图片/视频），九宫格。
 */
@Data
@TableName("moment_media")
public class MomentMedia {

    /** 类型：1 图片，2 视频 */
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long momentId;

    private Integer mediaType;

    /** 资源地址（本地 /uploads/... 或 OSS Key） */
    private String url;

    private String thumbnailUrl;

    private Integer width;
    private Integer height;

    /** 排序，从 0 开始 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
