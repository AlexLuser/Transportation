package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * AI 路线规划请求 DTO（预留大模型接口）
 * 传入出发地、目的地、车辆信息及约束条件，由大模型或路线规划 API 返回最优路线
 */
@Data
@Schema(description = "AI路线规划请求（预留大模型接口）")
public class AIRouteSuggestRequestDTO {

    @Schema(description = "物流路线ID（已有路线时传入，用于更新路线建议）")
    private Long routeId;

    // ---- 出发地信息 ----
    @Schema(description = "出发地地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originAddress;

    @Schema(description = "出发地纬度")
    private Double originLat;

    @Schema(description = "出发地经度")
    private Double originLng;

    // ---- 目的地信息 ----
    @Schema(description = "目的地地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String destAddress;

    @Schema(description = "目的地纬度")
    private Double destLat;

    @Schema(description = "目的地经度")
    private Double destLng;

    // ---- 车辆信息 ----
    @Schema(description = "车辆类型（小型货车/中型货车/大型货车等）")
    private String vehicleType;

    @Schema(description = "车辆载重（吨）")
    private Double vehicleLoadCapacity;

    @Schema(description = "货物重量（吨）")
    private Double cargoWeight;

    // ---- 时间约束 ----
    @Schema(description = "计划出发时间")
    private Date departureTime;

    @Schema(description = "要求最晚送达时间（可选，超时为异常）")
    private Date deadlineTime;

    // ---- 路线偏好 ----
    @Schema(description = "是否避免高速（默认false）")
    private Boolean avoidHighways;

    @Schema(description = "是否避免收费路段（默认false）")
    private Boolean avoidTolls;

    @Schema(description = "途经点列表（中转/休息站，可选）")
    private List<WaypointDTO> waypoints;

    @Schema(description = "特殊运输要求（如冷链、危化品等，给大模型的自然语言描述）")
    private String specialRequirements;

    // ---- 内部类：途经点 ----
    @Data
    @Schema(description = "途经点信息")
    public static class WaypointDTO {
        @Schema(description = "途经点名称")
        private String name;
        @Schema(description = "途经点地址")
        private String address;
        @Schema(description = "纬度")
        private Double lat;
        @Schema(description = "经度")
        private Double lng;
    }
}

