package com.fm.logistics.dto;

import lombok.Data;

/**
 * LLM 边费用校准结果（切入点一）
 */
@Data
public class EdgeCostCalibrationDTO {

    private Long linkId;

    /** 费用倍率（1.0=不变，1.3=涨价30%） */
    private double multiplier;

    /** LLM 给出的调整原因 */
    private String reason;

    /** false=LLM调用失败，已降级为 multiplier=1.0 */
    private boolean llmEnhanced;

    /** 接口返回填充：如「北京→上海」，便于表格展示 */
    private String routeLabel;
}
