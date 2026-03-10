package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * AI 智能调度优化请求 DTO（预留大模型接口）
 * 传入待配送订单列表和可用运输员列表，由大模型给出最优派单方案
 */
@Data
@Schema(description = "AI智能调度优化请求（预留大模型接口）")
public class AIDispatchOptimizeRequestDTO {

    @Schema(description = "待配送的路线列表（待出发状态）")
    private List<PendingDeliveryInfo> pendingDeliveries;

    @Schema(description = "可用运输员列表")
    private List<AvailableDriverInfo> availableDrivers;

    @Schema(description = "优化目标：MINIMIZE_TIME=最短时间, MINIMIZE_COST=最低成本, BALANCED=均衡")
    private String optimizationGoal;

    @Schema(description = "调度说明（给大模型的自然语言描述，如'优先处理今日截止单'）")
    private String dispatchNote;

    // ---- 待配送路线信息 ----
    @Data
    public static class PendingDeliveryInfo {
        @Schema(description = "物流路线ID")
        private Long routeId;
        @Schema(description = "订单ID")
        private Long orderId;
        @Schema(description = "发货地址")
        private String startAddress;
        @Schema(description = "收货地址")
        private String endAddress;
        @Schema(description = "发货地纬度")
        private Double startLat;
        @Schema(description = "发货地经度")
        private Double startLng;
        @Schema(description = "收货地纬度")
        private Double endLat;
        @Schema(description = "收货地经度")
        private Double endLng;
        @Schema(description = "优先级（1=普通，2=加急，3=紧急）")
        private Integer priority;
        @Schema(description = "要求最晚送达时间")
        private Date deadlineTime;
        @Schema(description = "货物重量（吨）")
        private Double cargoWeight;
    }

    // ---- 可用运输员信息 ----
    @Data
    public static class AvailableDriverInfo {
        @Schema(description = "运输员ID")
        private Long driverId;
        @Schema(description = "运输员姓名")
        private String driverName;
        @Schema(description = "当前位置纬度")
        private Double currentLat;
        @Schema(description = "当前位置经度")
        private Double currentLng;
        @Schema(description = "当前位置描述")
        private String currentAddress;
        @Schema(description = "车辆类型")
        private String vehicleType;
        @Schema(description = "车辆载重（吨）")
        private Double vehicleLoadCapacity;
        @Schema(description = "当前载货量（吨）")
        private Double currentLoad;
    }
}

