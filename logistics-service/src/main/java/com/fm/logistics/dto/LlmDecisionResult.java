package com.fm.logistics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * LLM 决策结果 DTO
 * 方案A（LLM_JUDGE）和方案B（LLM_WAYPOINT）共用此类，各自填充不同字段
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmDecisionResult {

    // ---- 方案A（多候选 + LLM 裁判）专用字段 ----

    /** LLM 选择的候选路线编号（1/2/3） */
    private Integer selectedCandidate;

    /** LLM 根据历史均速重新估算的预计行驶时间（ms） */
    private Long adjustedDurationMs;

    // ---- 方案B（LLM 路点 + A* 分段）专用字段 ----

    /** 路线策略名称，如"直达"/"绕行北侧"/"绕行外环" */
    private String strategy;

    /** LLM 输出的中间路点列表（0-2个），A* 将分段经过这些路点 */
    private List<WaypointPoint> waypoints;

    /** LLM 预估本次行驶平均时速（km/h） */
    private Double expectedSpeedKmh;

    // ---- 两方案通用字段 ----

    /** 决策依据说明（引用历史数据） */
    private String reasoning;

    /** 给用户展示的一句话路线摘要 */
    private String summary;

    /** 风险提示列表 */
    private List<String> warnings;

    /** 数据置信度：HIGH（样本>100）/ MEDIUM（10-100）/ LOW（<10） */
    private String confidenceLevel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WaypointPoint {
        private Double lat;
        private Double lon;
        private String label;
    }
}
