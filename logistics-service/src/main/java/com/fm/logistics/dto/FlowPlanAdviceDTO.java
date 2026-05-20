package com.fm.logistics.dto;

import lombok.Data;
import java.util.List;

/**
 * LLM 规划结果解读（切入点二）
 */
@Data
public class FlowPlanAdviceDTO {

    /** 瓶颈节点/边分析 */
    private String bottleneckAnalysis;

    /** 成本异常告警 */
    private String costAnomalyWarning;

    /** 操作建议列表 */
    private List<String> suggestions;

    /** 自然语言摘要（写入 flow_plan.llm_advice） */
    private String summary;

    /** false=LLM调用失败，返回空建议 */
    private boolean llmEnhanced;
}
