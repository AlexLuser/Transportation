package com.fm.common.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页结果DTO
 * 用于封装分页查询的结果
 */
@Data
public class PageResult<T> {
    /** 当前页码 */
    private Long current;
    
    /** 每页大小 */
    private Long size;
    
    /** 总记录数 */
    private Long total;
    
    /** 总页数 */
    private Long pages;
    
    /** 数据列表 */
    private List<T> records;
    
    /**
     * 创建分页结果
     * @param current 当前页码
     * @param size 每页大小
     * @param total 总记录数
     * @param records 数据列表
     */
    public PageResult(Long current, Long size, Long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;
        // 计算总页数
        this.pages = (total + size - 1) / size;  // 向上取整
    }
    
    /**
     * 创建空的分页结果
     */
    public static <T> PageResult<T> empty(Long current, Long size) {
        return new PageResult<>(current, size, 0L, List.of());
    }
}

