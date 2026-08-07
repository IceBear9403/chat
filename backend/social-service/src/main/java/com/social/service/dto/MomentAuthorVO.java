package com.social.service.dto;

import lombok.Data;

/**
 * 动态作者信息。
 */
@Data
public class MomentAuthorVO {

    private Long userId;
    private String nickname;
    private String avatar;
}
