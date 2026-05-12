package com.fm.logistics.dto;

import com.fm.logistics.entity.FlowPlan;
import com.fm.logistics.entity.FlowPlanItem;
import com.fm.logistics.entity.NationalHub;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 规划单详情（含 item 列表，供前端展示）
 */
@Data
public class FlowPlanDetailDTO {

    private FlowPlan plan;

    private List<FlowPlanItemVO> items;

    /** LLM 校准信息（每条边的倍率+原因） */
    private List<EdgeCostCalibrationDTO> calibrations;

    /** 第二次 LLM：对 MCMF 结果的解读（结构化） */
    private FlowPlanAdviceDTO llmFlowAdvice;

    @Data
    public static class FlowPlanItemVO {
        private Long linkId;
        private Long fromHubId;
        private Long toHubId;
        private String fromHubName;
        private String toHubName;
        private String transportMode;
        private int flowAmount;
        private int capacityDaily;
        /** 负载率 = flowAmount / capacityDaily */
        private double loadRate;
        private java.math.BigDecimal edgeCost;
        private java.math.BigDecimal baseCost;
        private java.math.BigDecimal totalCost;
    }
}
