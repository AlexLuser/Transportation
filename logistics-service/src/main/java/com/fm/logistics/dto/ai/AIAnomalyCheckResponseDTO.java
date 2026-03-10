package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * AI 异常检测响应 DTO（预留大模型接口）
 * 大模型根据轨迹数据分析路线偏离、长时间停车、超速、超时等异常
 */
@Data
@Schema(description = "AI异常检测响应（预留大模型接口）")
public class AIAnomalyCheckResponseDTO {

    @Schema(description = "路线ID")
    private Long routeId;

    @Schema(description = "是否存在异常")
    private Boolean hasAnomaly;

    @Schema(description = "检测到的异常列表")
    private List<AnomalyItem> anomalies;

    @Schema(description = "总体风险评分（0-100，越高越危险）")
    private Integer overallRiskScore;

    @Schema(description = "AI综合评估描述")
    private String overallAssessment;

    // ---- 内部类：异常项 ----
    @Data
    @Schema(description = "异常项详情")
    public static class AnomalyItem {

        @Schema(description = "异常类型：ROUTE_DEVIATION=路线偏离, LONG_STOP=长时间停车, " +
                "SPEED_ABNORMAL=速度异常, TIMEOUT=超时, SIGNAL_LOST=信号丢失")
        private String anomalyType;

        @Schema(description = "严重程度：1=LOW, 2=MEDIUM, 3=HIGH")
        private Integer severity;

        @Schema(description = "异常描述（大模型自然语言说明）")
        private String description;

        @Schema(description = "建议处理措施")
        private String suggestedAction;

        @Schema(description = "发生时间")
        private Date detectedAt;

        @Schema(description = "发生位置纬度")
        private Double lat;

        @Schema(description = "发生位置经度")
        private Double lng;

        @Schema(description = "发生位置描述")
        private String locationDesc;
    }
}

