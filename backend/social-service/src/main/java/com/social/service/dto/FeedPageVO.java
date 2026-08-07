package com.social.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信息流分页返回（游标分页）。
 */
@Data
public class FeedPageVO {

    private List<MomentVO> records;
    private boolean hasMore;
    /** 下一次拉取的游标（hasMore=false 时忽略） */
    private LocalDateTime nextCursorTime;
    private Long nextCursorId;
}
