package com.fm.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建物流路线请求 DTO
 * 由 order-service 在"订单发货"时调用，自动创建物流路线
 */
@Data
@Schema(description = "创建物流路线请求")
public class CreateRouteRequestDTO {

    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @Schema(description = "仓库ID（发货仓库，路线起点）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long warehouseId;

    @Schema(description = "发货地址（仓库地址，快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startAddress;

    @Schema(description = "发货地纬度（可选，用于精确路线规划）")
    private Double startLat;

    @Schema(description = "发货地经度（可选，用于精确路线规划）")
    private Double startLng;

    @Schema(description = "收货地址（快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endAddress;

    @Schema(description = "收货地纬度（可选）")
    private Double endLat;

    @Schema(description = "收货地经度（可选）")
    private Double endLng;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人电话")
    private String receiverPhone;
}

