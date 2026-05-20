package com.fm.logistics.dto;

import lombok.Data;

/**
 * 批次配送策略建议 DTO（方案A：LLM配送顾问）
 *
 * 由 POST /api/logistics/batches/advise 返回
 * LLM 分析订单分布、里程对比、Hub 可用性后，给出是否启用 Hub 模式的建议
 */
@Data
public class BatchAdviceDTO {

    /** LLM 建议是否启用 Hub-and-Spoke 模式 */
    private Boolean useHub;

    /** 推荐策略标识：HUB_AND_SPOKE | DIRECT_MULTI_DROP */
    private String strategy;

    /** LLM 给出的决策理由（中文，100字以内） */
    private String reason;

    /** 预计里程节省描述，如"预计节省约23%里程" */
    private String estimatedSaving;

    /** 配送紧迫度评估：HIGH | MEDIUM | LOW */
    private String urgencyLevel;

    /** 订单地理分散度简评，如"订单集中于浦东，建议合并" */
    private String dispersionAnalysis;

    /** 是否成功调用 LLM（false 表示降级为规则判断） */
    private Boolean llmEnhanced;

    /** 降级原因（llmEnhanced=false 时有值） */
    private String fallbackReason;

    /** 基于 Haversine 估算的直送总里程（米）：各辆车各自从仓库出发 */
    private Double directTotalDistanceM;

    /** 基于 Haversine 估算的合并配送总里程（米）：干线+末端合计 */
    private Double batchTotalDistanceM;
}
