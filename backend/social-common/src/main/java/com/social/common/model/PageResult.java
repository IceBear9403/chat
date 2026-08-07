package com.social.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页返回结构。信息流等长列表接口建议改用游标分页（阶段二实现）。
 *
 * <p>保持与具体 ORM 无关：MyBatis-Plus 的 IPage 适配由 service 层转换。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private boolean hasMore;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size, page * size < total);
    }
}
