package com.social.api.dto.request;

import com.social.service.dto.MomentMediaVO;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布动态请求。
 *
 * <p>type：0 图文，1 纯文字，2 转发；type=2 时需传 repostFrom，media 忽略。
 */
@Data
public class PublishMomentRequest {

    private String content;

    /** 类型：0 图文，1 纯文字，2 转发 */
    private Integer type;

    /** 可见范围：0 公开，1 仅好友，2 私密 */
    private Integer visibility;

    /** 图片/视频列表（最多 9 张） */
    @Size(max = 9, message = "最多上传 9 张图片")
    private List<MomentMediaVO> media;

    /** 转发来源动态 id（type=2 时必填） */
    private Long repostFrom;
}
