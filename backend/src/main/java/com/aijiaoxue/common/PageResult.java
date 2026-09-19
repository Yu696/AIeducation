package com.aijiaoxue.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页结果。
 *
 * <p>契约见 docs/API契约.md 第 0 节：{@code {"total":42,"page":1,"size":10,"records":[]}}。
 *
 * @param <T> 记录类型
 */
public class PageResult<T> {

    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private long page;
    /** 每页条数 */
    private long size;
    /** 当前页记录 */
    private List<T> records = new ArrayList<>();

    public PageResult() {
    }

    public PageResult(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records == null ? new ArrayList<>() : records;
    }

    /** 空分页结果，用于无权限或无数据的场景 */
    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(0L, page, size, new ArrayList<>());
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
