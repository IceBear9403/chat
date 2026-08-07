package com.social.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞操作结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeVO {

    private boolean liked;
    private long likeCount;
}
