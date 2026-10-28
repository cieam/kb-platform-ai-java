package com.example.kb.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页返回体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}
