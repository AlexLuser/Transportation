package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * AI 路线规划响应 DTO（预留大模型接口）
 */
@Data
@Schema(description = "AI路线规划响应（预留大模型接口）")
public class AIRouteSuggestResponseDTO {

    @Schema(description = "建议路线（GeoJSON LineString 格式的 JSON 字符串）")
    private String suggestedRoute;

    @Schema(description = "途经节点列表（包含名称、坐标、预计到达时间）")
    private List<RouteNodeDTO> routeNodes;

    @Schema(description = "预计总距离（km）")
    private Double estimatedDistance;

    @Schema(description = "预计总时长（分钟）")
    private Integer estimatedDuration;

    @Schema(description = "预计到达时间")
    private Date estimatedArrivalTime;

    @Schema(description = "路线风险等级：0=低风险，1=中风险，2=高风险")
    private Integer routeRiskLevel;

    @Schema(description = "风险因素列表（如'山区路段、易结冰'、'途经限行区域'等）")
    private List<String> riskFactors;

    @Schema(description = "备选路线列表（最多2条）")
    private List<AlternativeRouteDTO> alternativeRoutes;

    @Schema(description = "建议来源：AI / SYSTEM")
    private String suggestBy;

    @Schema(description = "建议置信度（0.0~1.0）")
    private Double confidence;

    @Schema(description = "AI分析说明（大模型给出的自然语言说明）")
    private String analysisNote;

    // ---- 内部类：路线节点 ----
    @Data
    public static class RouteNodeDTO {
        private String nodeName;
        private String nodeAddress;
        private Double lat;
        private Double lng;
        private Integer sequenceNo;
        private Date plannedArriveTime;
    }

    // ---- 内部类：备选路线 ----
    @Data
    public static class AlternativeRouteDTO {
        private String routeGeoJson;
        private Double estimatedDistance;
        private Integer estimatedDuration;
        private String description;
    }
}

