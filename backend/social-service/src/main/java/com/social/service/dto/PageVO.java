package com.social.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果（管理端用）。
 */
@Data
public class PageVO<T> {

    private List<T> records;
    private long page;
    private long size;
    private long total;
    /** 是否还有更多 */
    private boolean hasMore;
}
