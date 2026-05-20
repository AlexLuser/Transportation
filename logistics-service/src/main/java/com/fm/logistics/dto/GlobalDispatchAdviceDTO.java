package com.fm.logistics.dto;

import lombok.Data;

import java.util.List;

/**
 * LLM 全局调度建议 DTO
 * 针对整批待调度订单给出宏观调度策略建议
 */
@Data
public class GlobalDispatchAdviceDTO {

    /** 建议的批次数量 */
    private int suggestedBatchCount;

    /** 调度策略摘要 */
    private String strategy;

    /** LLM 给出的综合分析与理由 */
    private String reason;

    /** 各批次建议（与 ClusterResultDTO 的 clusterId 对应） */
    private List<BatchSuggestion> batchSuggestions;

    /** 预计里程节省（与直送对比，单位 km，LLM 估算） */
    private double estimatedSavingKm;

    /** 是否由 LLM 生成（false = 使用规则降级） */
    private boolean llmEnhanced;

    @Data
    public static class BatchSuggestion {
        /** 对应 clusterId */
        private int clusterId;
        /** 建议是否走 Hub 模式 */
        private boolean useHub;
        /** 建议的 hubId（若 useHub=true） */
        private Long suggestedHubId;
        /** Hub 名称 */
        private String suggestedHubName;
        /** 紧急程度：LOW / MEDIUM / HIGH */
        private String urgency;
        /** 具体建议说明 */
        private String note;
    }
}
