package com.fm.logistics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建配送批次请求 DTO
 *
 * 前端传入多个订单ID，后端执行：
 *   1. VRP 最优顺序规划
 *   2. 选择最近 Hub（useHub=true 时）
 *   3. 创建干线路线（仓库→Hub，segment_type=1）
 *   4. 为每个订单创建末端路线（Hub→客户，segment_type=2，初始待激活）
 */
@Data
public class CreateBatchRequestDTO {

    /**
     * 要合并的订单ID列表（至少2个，最多50个）
     * 这些订单必须属于同一仓库
     */
    private List<Long> orderIds;

    /** 发货仓库ID */
    private Long warehouseId;

    /** 仓库纬度（起点坐标） */
    private Double warehouseLat;

    /** 仓库经度（起点坐标） */
    private Double warehouseLng;

    /** 仓库地址（快照） */
    private String warehouseAddress;

    /**
     * 是否经 Hub 中转：true=是（推荐），false=干线司机直接多点配送
     */
    private Boolean useHub;

    /**
     * 是否为跨城干线到达后的末端配送批次。
     * true=包裹已在 NationalHub，直接从 Hub 出发末端配送，不再经本地分拨 Hub。
     * 此标记会强制 useHub=false，并将末端路线 segmentType 设为 2（末端配送）且立即可出发。
     */
    private Boolean isCrossCity;

    /**
     * 指定 Hub ID（可选，不传则系统自动选最近 Hub）
     */
    private Long hubId;

    /**
     * 路线规划策略（可选，不传则使用 application.yml 中配置的默认策略）
     * 可选值：A_STAR / LLM_JUDGE / LLM_WAYPOINT
     */
    private String strategy;

    /**
     * 计划发货时间（可选，用于 LLM 历史交通数据时段选取）
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime plannedShipTime;

    /**
     * 每个订单的目的地信息列表（与 orderIds 对应）
     * 前端在发起批次创建请求时，需提前查询好每个订单的收货地址坐标
     */
    private List<OrderItem> orderItems;

    /**
     * 单个订单的目的地信息（嵌套 DTO）
     */
    @Data
    public static class OrderItem {
        /** 订单ID */
        private Long orderId;
        /** 目的地纬度 */
        private Double endLat;
        /** 目的地经度 */
        private Double endLng;
        /** 目的地地址 */
        private String endAddress;
        /** 收货人姓名 */
        private String receiverName;
        /** 收货人电话 */
        private String receiverPhone;
    }
}
