package com.fm.logistics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建物流路线请求 DTO
 * 由 order-service 在"订单发货"时调用
 */
@Data
public class CreateRouteRequestDTO {

    /** 订单ID（必填） */
    private Long orderId;

    /** 发货仓库ID（必填） */
    private Long warehouseId;

    /** 出发地址（仓库地址快照，必填） */
    private String startAddress;

    /** 出发地纬度（可选） */
    private Double startLatitude;

    /** 出发地经度（可选） */
    private Double startLongitude;

    /** 收货地址（快照，必填） */
    private String endAddress;

    /** 收货地纬度（可选） */
    private Double endLatitude;

    /** 收货地经度（可选） */
    private Double endLongitude;

    /** 收货人姓名（可选） */
    private String receiverName;

    /** 收货人电话（可选） */
    private String receiverPhone;

    /**
     * 计划发货时间（可选）
     * 不传则取服务器当前时间，用于 LLM 历史交通数据时段选取
     * 格式：yyyy-MM-dd'T'HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime plannedShipTime;

    // ── Hub-and-Spoke 扩展字段（批次模式下才传入）──────────────

    /**
     * 所属批次ID（批次模式下传入）
     */
    private Long batchId;

    /**
     * 路线段类型：
     *   0 = 独立单订单（默认，不传则为0）
     *   1 = 干线（仓库 → Hub）
     *   2 = 末端（Hub → 客户）
     */
    private Integer segmentType;

    /**
     * 中转站ID：
     *   干线路线（type=1）时，Hub 是终点
     *   末端路线（type=2）时，Hub 是起点
     */
    private Long hubId;

    /**
     * 跨城直达末端路线标记：true 时即使 segmentType=2 也将 routeStatus 置为 0（待出发）。
     * 普通批次的 segType=2 末端路线初始为 -1（待激活），需等干线到达 Hub 后才激活；
     * 而跨城到达批次包裹已在 Hub，路线创建时即可立即出发，无需等激活事件。
     */
    private Boolean crossCityDirect;

    // ── 多停靠末端路线字段（stop_count > 1 时传入）──────────────

    /** 批次内末端分组编号（0,1,2...） */
    private Integer groupIndex;

    /** 本路线停靠点数量（>1 表示多停靠末端路线） */
    private Integer stopCount;

    /**
     * 多停靠点有序列表（JSON字符串）
     * 格式：[{seq,orderId,address,lat,lng,receiverName,receiverPhone}, ...]
     * stop_count=1 时传 null
     */
    private String waypoints;
}
