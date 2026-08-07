package com.social.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天历史分页（游标按 id 倒序，返回升序时间线）。
 */
@Data
public class ChatHistoryVO {

    private List<MessageVO> records;
    /** 是否还有更早的消息 */
    private boolean hasMore;
}
