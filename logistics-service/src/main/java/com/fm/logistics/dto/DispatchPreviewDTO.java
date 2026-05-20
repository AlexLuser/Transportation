package com.fm.logistics.dto;

import lombok.Data;

import java.util.List;

/**
 * 调度预览结果 DTO — 聚类 + LLM 建议，供管理员确认后执行
 */
@Data
public class DispatchPreviewDTO {

    /** 总待调度订单数 */
    private int totalOrders;

    /** 建议批次数 */
    private int suggestedBatchCount;

    /** 所用聚类数 k */
    private int kUsed;

    /** K-Means 聚类结果 */
    private List<ClusterResultDTO> clusters;

    /** LLM 全局调度建议 */
    private GlobalDispatchAdviceDTO llmAdvice;

    /** 是否由 LLM 增强 */
    private boolean llmEnhanced;
}
