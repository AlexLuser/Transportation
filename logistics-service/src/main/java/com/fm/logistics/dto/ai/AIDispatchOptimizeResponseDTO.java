package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * AI 智能调度优化响应 DTO（预留大模型接口）
 */
@Data
@Schema(description = "AI智能调度优化响应（预留大模型接口）")
public class AIDispatchOptimizeResponseDTO {

    @Schema(description = "派单分配建议列表")
    private List<AssignmentSuggestion> assignments;

    @Schema(description = "未能分配的路线ID列表（运力不足时）")
    private List<Long> unassignedRouteIds;

    @Schema(description = "预计总运输距离（km）")
    private Double totalEstimatedDistance;

    @Schema(description = "预计总运输时长（分钟）")
    private Integer totalEstimatedMinutes;

    @Schema(description = "调度效率评分（0-100）")
    private Integer optimizationScore;

    @Schema(description = "AI调度分析说明（大模型自然语言描述调度逻辑）")
    private String analysisNote;

    // ---- 派单建议 ----
    @Data
    public static class AssignmentSuggestion {
        @Schema(description = "物流路线ID")
        private Long routeId;
        @Schema(description = "建议指派的运输员ID")
        private Long suggestedDriverId;
        @Schema(description = "运输员姓名")
        private String driverName;
        @Schema(description = "预计取货时间")
        private Date estimatedPickupTime;
        @Schema(description = "预计送达时间")
        private Date estimatedDeliveryTime;
        @Schema(description = "预计运输距离（km）")
        private Double estimatedDistance;
        @Schema(description = "指派理由（大模型说明）")
        private String reason;
    }
}

